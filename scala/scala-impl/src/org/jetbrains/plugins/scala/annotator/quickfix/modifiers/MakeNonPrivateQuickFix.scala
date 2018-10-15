package org.jetbrains.plugins.scala
package annotator.quickfix.modifiers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * Nikolay.Tropin
 * 11/6/13
 */
class MakeNonPrivateQuickFix(member: ScModifierListOwner, toProtected: Boolean) extends IntentionAction {

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    member.setModifierProperty("private", value = false)
    if (toProtected) member.setModifierProperty("protected")
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)
    CodeStyleManager.getInstance(project).adjustLineIndent(file, member.getModifierList.getTextRange.getStartOffset)
  }

  override def getText: String = if (toProtected) "Make field protected" else "Make field public"

  override def getFamilyName: String = "Make field non-private"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    member.isValid && member.getContainingFile == file && member.getManager.isInProject(file)

  override def startInWriteAction(): Boolean = true

}
