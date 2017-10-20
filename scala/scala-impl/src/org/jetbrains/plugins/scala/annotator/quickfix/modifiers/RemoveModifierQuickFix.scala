package org.jetbrains.plugins.scala
package annotator
package quickfix.modifiers


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.10.2008
 */

class RemoveModifierQuickFix(method: ScModifierListOwner, modifier: String) extends IntentionAction{
  override def getText: String = ScalaBundle.message("remove.modifier.fix", modifier)

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    method.isValid && method.getManager.isInProject(file)

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    method.setModifierProperty(modifier, value = false)
    //Should be handled by autoformatting
    CodeStyleManager.getInstance(method.getProject).reformatText(method.getContainingFile,
      method.getModifierList.getTextRange.getStartOffset,
      method.getModifierList.getTextRange.getEndOffset)
  }

  override def getFamilyName: String =
    ScalaBundle.message("remove.modifier.fix", modifier)
}