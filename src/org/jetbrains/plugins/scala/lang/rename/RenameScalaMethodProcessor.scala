package org.jetbrains.plugins.scala.lang.rename

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{Messages, DialogWrapper}
import com.intellij.psi.{PsiMember, PsiMethod, PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.{MemberHidesStaticImportUsageInfo, RenamePsiElementProcessor, RenameJavaMethodProcessor}
import java.awt.{GridLayout, BorderLayout}

import java.util.{List, Map}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import javax.swing._
import psi.api.statements.ScFunction
import psi.api.toplevel.ScNamedElement
import psi.impl.search.ScalaOverridengMemberSearch

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaMethodProcessor extends RenameJavaMethodProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element.isInstanceOf[ScFunction]

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]): Unit = {
    val function = element match {case x: ScFunction => x case _ => return}
    for (elem <- ScalaOverridengMemberSearch.search(function, true)) {
      val overriderName = elem.getName
      val baseName = function.getName
      val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (newOverriderName != null) {
        allRenames.put(elem, newOverriderName)
      }
    }
  }


  override def findCollisionsAgainstNewName(memberToRename: PsiMember, newName: String, result: List[_ >: MemberHidesStaticImportUsageInfo]): Unit = {
    //todo
  }


  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    val function: ScFunction = element match {case x: ScFunction => x case _ => return element}
    val signs = function.superSignatures
    if (signs.length == 0) return function
    val dialog = new WarningDialog(function.getProject, function.getName, signs.length)
    dialog.show

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) return function
    //todo: add choosing superMember

    return null
  }
  private class WarningDialog(project: Project, name: String, len: Int) extends DialogWrapper(project, true) {
    setTitle(IdeBundle.message("title.warning"))
    setButtonsAlignment(SwingConstants.CENTER)
    setOKButtonText(CommonBundle.getYesButtonText())
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
      labelsPanel.add(new JLabel(ScalaBundle.message("method.has.supers", name)))
      panel.add(labelsPanel, BorderLayout.CENTER)
      return panel
    }
  }

  def capitalize(text: String): String = Character.toUpperCase(text.charAt(0)) + text.substring(1)
}

