package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDirectory, PsiClass, PsiElement}
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.refactoring.move.MoveCallback
import lang.psi.impl.ScalaFileImpl
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.refactoring.util.{TextOccurrencesUtil, CommonRefactoringUtil}
import com.intellij.refactoring.move.moveClassesOrPackages._
import javax.swing._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import java.awt.event.{ActionEvent, ActionListener}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.annotations.{NotNull, Nullable}
import com.intellij.refactoring.{MoveDestination, HelpID, JavaRefactoringSettings}
import java.awt.BorderLayout

/**
 * @author Alefas
 * @since 02.11.12
 */
class ScalaMoveClassesOrPackagesHandler extends JavaMoveClassesOrPackagesHandler {
  override def doMove(project: Project, elements: Array[PsiElement], targetContainer: PsiElement, callback: MoveCallback) {
    def refactoringIsNotSupported() {
      Messages.showErrorDialog(ScalaBundle.message("move.to.inner.is.not.supported"), ScalaBundle.message("move.to.inner.is.not.supported.title"))
    }
    targetContainer match {
      case td: ScTypeDefinition =>
        refactoringIsNotSupported()
        return
      case clazz: PsiClass =>
        if (elements.exists(_.isInstanceOf[ScTypeDefinition])) {
          refactoringIsNotSupported()
          return
        }
      case _ =>
    }
    ScalaFileImpl.performMoveRefactoring {
      super.doMove(project, elements, targetContainer, callback)
    }
  }

  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement): Boolean = {
    //sort of hack to save destinations here, need to be sure that it is called
    val scalaElements = elements.filter(_.getLanguage.isInstanceOf[ScalaLanguage])
    targetContainer match {
      case dir: PsiDirectory => scalaElements.foreach(ScalaMoveUtil.saveMoveDestination(_, dir))
      case _ =>
    }
    elements.length == scalaElements.length && super.canMove(elements, targetContainer)
  }

  protected override def doMoveWithMoveClassesDialog(project: Project,
                                          adjustedElements: Array[PsiElement],
                                          initialTargetElement: PsiElement,
                                          moveCallback: MoveCallback) {

    import scala.collection.JavaConversions._
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, adjustedElements.toSeq, true)) {
      return
    }
    val initialTargetPackageName: String = MoveClassesOrPackagesImpl.getInitialTargetPackageName(initialTargetElement, adjustedElements)
    val initialTargetDirectory: PsiDirectory = MoveClassesOrPackagesImpl.getInitialTargetDirectory(initialTargetElement, adjustedElements)
    val isTargetDirectoryFixed: Boolean = initialTargetDirectory == null
    val searchTextOccurences: Boolean = adjustedElements.exists(TextOccurrencesUtil.isSearchTextOccurencesEnabled)
    val moveDialog: MoveClassesOrPackagesDialog =
      new MoveClassesOrPackagesDialog(project, searchTextOccurences, adjustedElements, initialTargetElement, moveCallback) {
        override def createCenterPanel(): JComponent = {
          addMoveCompanionChb(super.createCenterPanel(), adjustedElements)
        }

        override def createMoveToPackageProcessor(destination: MoveDestination,
                                                  elementsToMove: Array[PsiElement],
                                                  callback: MoveCallback): MoveClassesOrPackagesProcessor = {

          new ScalaMoveClassesOrPackagesProcessor(project, elementsToMove, destination, isSearchInComments, searchTextOccurences, callback)
        }
      }
    val searchInComments: Boolean = JavaRefactoringSettings.getInstance.MOVE_SEARCH_IN_COMMENTS
    val searchForTextOccurences: Boolean = JavaRefactoringSettings.getInstance.MOVE_SEARCH_FOR_TEXT
    moveDialog.setData(adjustedElements, initialTargetPackageName, initialTargetDirectory, isTargetDirectoryFixed, initialTargetElement == null, searchInComments, searchForTextOccurences, HelpID.getMoveHelpID(adjustedElements(0)))
    moveDialog.show()
  }

  @NotNull
  protected override def createMoveClassesOrPackagesToNewDirectoryDialog(@NotNull directory: PsiDirectory,
                                                                         elementsToMove: Array[PsiElement], 
                                                                         moveCallback: MoveCallback): DialogWrapper = {
    new MoveClassesOrPackagesToNewDirectoryDialog(directory, elementsToMove, moveCallback) {
      protected override def createCenterPanel(): JComponent = {
        addMoveCompanionChb(super.createCenterPanel(), elementsToMove)
      }

      protected override def createMoveClassesOrPackagesProcessor(project: Project,
                                                                  elements: Array[PsiElement],
                                                                  moveDestination: MoveDestination,
                                                                  searchInComments: Boolean,
                                                                  searchInNonJavaFiles: Boolean,
                                                                  moveCallback: MoveCallback): MoveClassesOrPackagesProcessor = {

        new ScalaMoveClassesOrPackagesProcessor(project, elements, moveDestination, searchInComments, searchInNonJavaFiles, moveCallback)
      }
    }
  }

  private def addMoveCompanionChb(@Nullable panel: JComponent, elements: Iterable[PsiElement]): JComponent = {
    val companions = for {
      elem <- elements.collect{case psiClass: PsiClass => psiClass}
      companion <- ScalaPsiUtil.getBaseCompanionModule(elem)
    } yield companion
    if (companions.nonEmpty) {
      val result = new JPanel(new BorderLayout())
      if (panel != null) result.add(panel, BorderLayout.NORTH)
      val chbMoveCompanion = new JCheckBox(ScalaBundle.message("move.with.companion"))
      chbMoveCompanion.setSelected(ScalaApplicationSettings.getInstance().MOVE_COMPANION)
      chbMoveCompanion.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          ScalaApplicationSettings.getInstance().MOVE_COMPANION = chbMoveCompanion.isSelected
        }
      })
      chbMoveCompanion.setMnemonic('t')
      result.add(chbMoveCompanion, BorderLayout.WEST)
      result
    }
    else panel
  }
}
