package org.jetbrains.plugins.scala.annotator.quickfix.modifiers


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor

import com.intellij.openapi.project.Project

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.PsiFile
import lang.psi.api.statements.ScFunction
import lang.psi.api.toplevel.ScModifierListOwner

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.10.2008
 */

class RemoveModifierQuickFix(method: ScModifierListOwner, modifier: String) extends IntentionAction{
  def getText: String = ScalaBundle.message("remove.modifier.fix", Array[Object](modifier))

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = method.isValid && method.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    method.setModifierProperty(modifier, false)
    //Should be handled by autoformatting
    CodeStyleManager.getInstance(method.getProject()).reformat(method.getModifierList)
  }

  def getFamilyName: String = ScalaBundle.message("remove.modifier.fix", Array[Object](modifier))
}