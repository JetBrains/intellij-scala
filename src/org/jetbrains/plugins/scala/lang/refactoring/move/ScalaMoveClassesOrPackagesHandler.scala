package org.jetbrains.plugins.scala
package lang.refactoring.move

import java.awt.BorderLayout
import java.awt.event.{ActionEvent, ActionListener}
import javax.swing._

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement}
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages._
import com.intellij.refactoring.util.{CommonRefactoringUtil, TextOccurrencesUtil}
import com.intellij.refactoring.{HelpID, JavaRefactoringSettings, MoveDestination}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getBaseCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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
      case _: ScTypeDefinition =>
        refactoringIsNotSupported()
        return
      case _: PsiClass =>
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
    val scalaElements = elements.filter(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
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

          new MoveClassesOrPackagesProcessor(project, elementsToMove, destination, isSearchInComments, searchTextOccurences, callback)
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

        new MoveClassesOrPackagesProcessor(project, elements, moveDestination, searchInComments, searchInNonJavaFiles, moveCallback)
      }
    }
  }

  private def addMoveCompanionChb(@Nullable panel: JComponent, elements: Array[PsiElement]): JComponent = {
    val companionsExist = elements.collect {
      case definition: ScTypeDefinition => definition
    }.exists {
      getBaseCompanionModule(_).isDefined
    }

    if (companionsExist) {
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
