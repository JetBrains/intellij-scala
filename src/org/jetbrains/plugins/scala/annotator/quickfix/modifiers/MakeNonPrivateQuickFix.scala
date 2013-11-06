package org.jetbrains.plugins.scala
package annotator.quickfix.modifiers

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiFile}

/**
 * Nikolay.Tropin
 * 11/6/13
 */
class MakeNonPrivateQuickFix(member: ScModifierListOwner, toProtected: Boolean) extends IntentionAction {

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    member.setModifierProperty("private", value = false)
    if (toProtected) member.setModifierProperty("protected", value = true)
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)
    CodeStyleManager.getInstance(project).adjustLineIndent(file, member.getModifierList.getTextRange.getStartOffset)
  }

  def getText: String = if (toProtected) "Make field protected" else "Make field public"

  def getFamilyName: String = "Make field non-private"

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    member.isValid && member.getContainingFile == file && member.getManager.isInProject(file)

  def startInWriteAction(): Boolean = true

}
