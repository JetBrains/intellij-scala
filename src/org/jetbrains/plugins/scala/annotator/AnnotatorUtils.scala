package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotation
import lang.psi.api.expr.ScExpression
import lang.psi.api.base.types.ScTypeElement
import quickfix.ReportHighlightingErrorQuickFix

/**
 * @author Aleksander Podkhalyuzin
 * @date 25.03.2009
 */

private[annotator] object AnnotatorUtils {
  def proccessError(error: String, element: PsiElement, holder: AnnotationHolder, fixes: IntentionAction*) {
    proccessError(error, element.getTextRange, holder, fixes: _*)
  }

  def proccessError(error: String, range: TextRange, holder: AnnotationHolder, fixes: IntentionAction*) {
    val annotation = holder.createErrorAnnotation(range, error)
    annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    for (fix <- fixes) annotation.registerFix(fix)
  }

  def proccessWarning(error: String, element: PsiElement, holder: AnnotationHolder, fixes: IntentionAction*) {
    proccessWarning(error, element.getTextRange, holder, fixes: _*)
  }

  def proccessWarning(error: String, range: TextRange, holder: AnnotationHolder, fixes: IntentionAction*) {
    val annotation: Annotation = holder.createWarningAnnotation(range, error)
    annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    for (fix <- fixes) annotation.registerFix(fix)
  }

  def checkConformance(expression: ScExpression, typeElement: ScTypeElement, holder: AnnotationHolder) {
    expression.getTypeAfterImplicitConversion().tr.foreach {actual =>
      val expected = typeElement.calcType
      if (!actual.conforms(expected)) {
        val annotation = holder.createErrorAnnotation(expression,
          "Type mismatch, found: %s, required: %s".format(actual.presentableText, expected.presentableText))
        annotation.registerFix(ReportHighlightingErrorQuickFix)
      }
    }
  }
}