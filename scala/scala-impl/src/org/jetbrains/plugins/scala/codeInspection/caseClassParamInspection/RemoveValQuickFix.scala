package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

class RemoveValQuickFix(param: ScClassParameter)
        extends AbstractFixOnPsiElement(ScalaBundle.message("remove.val"), param) {

  override protected def doApplyFix(p: ScClassParameter)
                                   (implicit project: Project): Unit = {
    p.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
    CodeStyleManager.getInstance(p.getProject).reformatText(p.getContainingFile,
      p.getModifierList.getTextRange.getStartOffset,
      p.getModifierList.getTextRange.getEndOffset)
  }
}

class RemoveValFromEnumeratorIntentionAction(enum: ScEnumerator) extends IntentionAction {

  override def getText: String = "Remove unnecessary 'val'"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!enum.isValid) return
    enum.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  override def startInWriteAction(): Boolean = true

  override def getFamilyName: String = "Remove 'val' from enumerator"
}

class RemoveValFromGeneratorIntentionAction(enum: ScGenerator) extends IntentionAction {

  override def getText: String = "Remove unnecessary 'val'"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!enum.isValid) return
    enum.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  override def startInWriteAction() = true

  override def getFamilyName: String = "Remove 'val' from generator"
}