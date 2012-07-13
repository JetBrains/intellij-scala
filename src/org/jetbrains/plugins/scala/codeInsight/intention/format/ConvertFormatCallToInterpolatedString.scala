package org.jetbrains.plugins.scala.codeInsight.intention.format

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, MethodInvocation, ScInfixExpr}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.format.Format
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class ConvertFormatCallToInterpolatedString extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert format call to interpolated string"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val invocation = PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false)
    setText(getFamilyName)
    Format.extractFormatCall(invocation).isDefined
  }

  override def invoke(p1: Project, p2: Editor, p3: PsiElement) {
    val invocation = PsiTreeUtil.getParentOfType(p3, classOf[MethodInvocation], false)
    val result = {
      val presentation = {
        val Some((literal, args)) = Format.extractFormatCall(invocation)
        val parts = Format.parseFormatCall(literal.getValue.asInstanceOf[String], args)
        val content = Format.formatAsInterpolatedString(parts)
        val prefix = if (Format.isFormattingRequired(parts)) "f" else "s"
        prefix + '"' + content + '"'
      }
      ScalaPsiElementFactory.createExpressionFromText(presentation, p3.getManager)
    }
    invocation.replace(result)
  }
}
