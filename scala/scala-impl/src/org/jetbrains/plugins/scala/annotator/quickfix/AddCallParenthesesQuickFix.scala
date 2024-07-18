package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class AddCallParenthesesQuickFix(expression: ScExpression)
  extends LocalQuickFixAndIntentionActionOnPsiElement(expression) {

  override def getText: String = ScalaInspectionBundle.message("add.call.parentheses")

  override def getFamilyName: String = getText

  override def invoke(
    project: Project,
    psiFile: PsiFile,
    editor: Editor,
    startElement: PsiElement,
    endElement: PsiElement
  ): Unit = {
    if (!IntentionPreviewUtils.prepareElementForWrite(psiFile))
      return

    if (!startElement.isValid)
      return

    val target = expression.getParent match {
      case postfix: ScPostfixExpr => postfix
      case call: ScGenericCall => call
      case _ => expression
    }

    val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${target.getText}()", expression)(project)
    target.replace(replacement)
  }
}
