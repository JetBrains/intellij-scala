package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{Messages, DialogWrapper}
import com.intellij.refactoring.rename.RenameJavaMethodProcessor
import java.awt.{GridLayout, BorderLayout}

import javax.swing._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import psi.impl.search.ScalaOverridengMemberSearch
import psi.api.base.ScPrimaryConstructor
import collection.mutable.ArrayBuffer
import psi.fake.FakePsiMethod
import extensions.toPsiNamedElementExt
import com.intellij.psi.{PsiMethod, PsiElement}
import java.util
import com.intellij.openapi.util.Pass
import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.psi.search.PsiElementProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import scala.Some
import com.intellij.usageView.UsageInfo
import com.intellij.refactoring.listeners.RefactoringElementListener

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaMethodProcessor extends RenameJavaMethodProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case _: FakePsiMethod => false
    case _: ScFunction | _:ScPrimaryConstructor => true
    case _ => false
  }

  override def findReferences(element: PsiElement) =
    ScalaRenameUtil.filterAliasedReferences(super.findReferences(element))

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    val function = element match {case x: ScFunction => x case _ => return}
    val buff = new ArrayBuffer[ScFunction]
    function.getGetterOrSetterFunction match {
      case Some(function2) => buff += function2
      case _ =>
    }
    for (elem <- ScalaOverridengMemberSearch.search(function, deep = true)) {
      val overriderName = elem.name
      val baseName = function.name
      //val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (overriderName == baseName) {
        allRenames.put(elem, newName)
        elem match {
          case fun: ScFunction => fun.getGetterOrSetterFunction match {
            case Some(function2) => buff += function2
            case _ =>
          }
          case _ =>
        }
      }
    }
    if (!buff.isEmpty) {
      val dialog = new WarningDialog(function.getProject,
        ScalaBundle.message("rename.getters.and.setters.title"))
      dialog.show()

      if (dialog.getExitCode == DialogWrapper.OK_EXIT_CODE) {
        val shortNewName = if (newName.endsWith("_=")) newName.substring(0, newName.length - 2) else newName
        for (elem <- buff) {
          allRenames.put(elem, if (elem.name.endsWith("_=")) shortNewName + "_=" else shortNewName)
        }
      }
    }

    ScalaElementToRenameContributor.getAll(element, newName, allRenames)
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    val guess = ScalaRenameUtil.findSubstituteElement(element)
    if (guess != element) guess else SuperMethodWarningUtil.checkSuperMethod(element.asInstanceOf[PsiMethod] , "Rename")
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[PsiElement]) {
    val guess = ScalaRenameUtil.findSubstituteElement(element)
    if (guess != element) renameCallback.pass(guess)
    else SuperMethodWarningUtil.checkSuperMethod(element.asInstanceOf[PsiMethod], "Rename", new PsiElementProcessor[PsiMethod] {
      def execute(method: PsiMethod): Boolean = {
        if (!canProcessElement(method)) false
        else {
          renameCallback.pass(method)
          false
        }
      }
    }, editor)
  }

  private class WarningDialog(project: Project, text: String) extends DialogWrapper(project, true) {
    setTitle(IdeBundle.message("title.warning"))
    setButtonsAlignment(SwingConstants.CENTER)
    setOKButtonText(CommonBundle.getYesButtonText)
    init()

    def createCenterPanel: JComponent = null

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

  def capitalize(text: String): String = Character.toUpperCase(text.charAt(0)) + text.substring(1)

  override def setToSearchInComments(element: PsiElement, enabled: Boolean) {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = enabled
  }

  override def isToSearchInComments(psiElement: PsiElement): Boolean = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS
  }

  override def renameElement(psiElement: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener) {
    ScalaRenameUtil.doRenameGenericNamedElement(psiElement, newName, usages, listener)
  }
}

