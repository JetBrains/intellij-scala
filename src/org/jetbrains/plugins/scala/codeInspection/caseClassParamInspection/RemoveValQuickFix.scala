package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import lang.psi.api.expr.{ScGenerator, ScEnumerator}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class RemoveValQuickFix(param: ScClassParameter) extends LocalQuickFix{
  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!param.isValid) return
    param.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
    CodeStyleManager.getInstance(param.getProject).reformatText(param.getContainingFile,
      param.getModifierList.getTextRange.getStartOffset,
      param.getModifierList.getTextRange.getEndOffset)
  }

  def getName: String = "Remove 'val'"

  def getFamilyName: String = "Remove 'val'"
}

class RemoveValFromEnumeratorIntentionAction(enum: ScEnumerator) extends IntentionAction {
  def getText = "Remove unnecessary 'val'"

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!enum.isValid) return
    enum.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  def startInWriteAction() = true

  def getFamilyName: String = "Remove 'val' from enumerator"
}

class RemoveValFromGeneratorIntentionAction(enum: ScGenerator) extends IntentionAction {
  def getText = "Remove unnecessary 'val'"

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!enum.isValid) return
    enum.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  def startInWriteAction() = true

  def getFamilyName: String = "Remove 'val' from generator"
}