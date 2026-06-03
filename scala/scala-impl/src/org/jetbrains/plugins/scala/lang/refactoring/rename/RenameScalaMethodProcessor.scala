package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.Pass
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.{RenameJavaMethodProcessor, RenamePsiElementProcessor}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.awt.{BorderLayout, GridLayout}
import java.util
import javax.swing._
import scala.collection.mutable.ArrayBuffer

class RenameScalaMethodProcessor extends RenameJavaMethodProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = RenameScalaMethodProcessor.canProcessElement(element)

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    val guess = ScalaRenameUtil.findSubstituteElement(element)
    guess match {
      case Some(guess) if guess != element => guess
      case _ => RenameSuperMembersUtil.chooseSuper(element.asInstanceOf[ScNamedElement])
    }
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[_ >: PsiElement]): Unit = {
    val named = element match {case named: ScNamedElement => named; case _ => return}
    val guess = ScalaRenameUtil.findSubstituteElement(element)

    guess match {
      case Some(guess) if guess != element =>
        renameCallback.pass(guess)
      case _ =>
        RenameSuperMembersUtil.chooseAndProcessSuper(
          named,
          (named: PsiNamedElement) => {
            renameCallback.pass(named)
            false
          },
          editor
        )
    }
  }

  override def renameElement(psiElement: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener): Unit = {
    ScalaRenameUtil.doRenameGenericNamedElement(psiElement, newName, usages, listener)
  }
}

object RenameScalaMethodProcessor {
  def canProcessElement(element: PsiElement): Boolean = element match {
    case _: FakePsiMethod => false
    case _: ScFunction | _:ScPrimaryConstructor => true
    case _ => false
  }
}

class PrepareRenameScalaMethodProcessor extends RenamePsiElementProcessor {
  override def canProcessElement(element: PsiElement): Boolean = RenameScalaMethodProcessor.canProcessElement(element)

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit = {
    val function = element match {case x: ScFunction => x case _ => return}
    val buff = new ArrayBuffer[PsiNamedElement]
    val getterOrSetter = getGetterOrSetterFunction(function) match {
      case Some(function2) =>
        buff += function2
        Some(function2)
      case _ => None
    }
    for (elem <- ScalaOverridingMemberSearcher.search(function, deep = true)) {
      allRenames.put(elem, newName)
      elem match {
        case fun: ScFunction => getGetterOrSetterFunction(fun) match {
          case Some(function2) => buff += function2
          case _ =>
        }
        case _ =>
      }
    }
    for {
      setter <- getterOrSetter
      elem <- ScalaOverridingMemberSearcher.search(setter, deep = true)
    } {
      buff += elem
    }
    if (buff.nonEmpty) {
      def addGettersAndSetters(): Unit = {
        def nameWithSetterSuffix(oldName: String, newName: String): String = {
          val newSuffix = ScalaRenameUtil.setterSuffix(newName)
          val oldSuffix = ScalaRenameUtil.setterSuffix(oldName)
          if (newSuffix == "" && oldSuffix != "") newName + oldSuffix //user typed name without suffix for setter and chose to rename getter too
          else if (newSuffix != "" && oldSuffix == "") newName.stripSuffix(newSuffix) //for renaming getters
          else newName
        }
        import scala.jdk.CollectionConverters._
        for (elem <- allRenames.keySet.asScala ++ buff) {
          val oldName = ScalaNamesUtil.scalaName(elem)
          allRenames.put(elem, nameWithSetterSuffix(oldName, newName))
        }
      }

      if (ApplicationManager.getApplication.isUnitTestMode) {
        addGettersAndSetters()
      } else {
        val dialog = new WarningDialog(function.getProject, ScalaBundle.message("rename.getters.and.setters.title"))
        dialog.show()
        if (dialog.getExitCode == DialogWrapper.OK_EXIT_CODE || ApplicationManager.getApplication.isUnitTestMode) {
          addGettersAndSetters()
        }
      }
    }
    RenameSuperMembersUtil.prepareSuperMembers(newName, allRenames)
    ScalaElementToRenameContributor.addAllElements(element, newName, allRenames)
  }

  def getGetterOrSetterFunction(f: ScFunction): Option[ScFunction] = {
    f.containingClass match {
      case clazz: ScTemplateDefinition =>
        val name = f.name
        if (name.endsWith("_=")) {
          clazz.functions.find(_.name == name.substring(0, name.length - 2))
        } else if (!f.hasParameterClause) {
          clazz.functions.find(_.name == name + "_=")
        } else None
      case _ => None
    }
  }


  private class WarningDialog(project: Project, text: String) extends DialogWrapper(project, true) {
    setTitle(IdeBundle.message("title.warning"))
    setOKButtonText(CommonBundle.getYesButtonText)
    init()

    override def createCenterPanel: JComponent = null

    override def createNorthPanel: JComponent = {
      val panel = new JPanel(new BorderLayout)
      panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
      val icon = Messages.getWarningIcon
      if (icon != null) {
        val iconLabel = new JLabel(Messages.getQuestionIcon)
        panel.add(iconLabel, BorderLayout.WEST)
      }
      val labelsPanel = new JPanel(new GridLayout(0, 1, 0, 0))
      labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10))
      labelsPanel.add(new JLabel(text))
      panel.add(labelsPanel, BorderLayout.CENTER)
      panel
    }
  }
}

