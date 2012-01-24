package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotation
import lang.psi.api.base.types.ScTypeElement
import quickfix.ReportHighlightingErrorQuickFix
import lang.psi.api.toplevel.ScTypeParametersOwner
import lang.psi.api.statements.params.ScParameters
import lang.psi.api.expr.{ScBlockExpr, ScExpression}

/**
 * @author Aleksander Podkhalyuzin
 * Date: 25.03.2009
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
        val expr = expression match {
          case b: ScBlockExpr => b.getRBrace.map(_.getPsi).getOrElse(b)
          case _ => expression
        }
        val annotation = holder.createErrorAnnotation(expr,
          "Type mismatch, found: %s, required: %s".format(actual.presentableText, expected.presentableText))
        annotation.registerFix(ReportHighlightingErrorQuickFix)
      }
    }
  }

  def checkImplicitParametersAndBounds(paramOwner: ScTypeParametersOwner, parameters: Option[ScParameters], holder: AnnotationHolder) {
    val hasImplicitBound = paramOwner.typeParameters.exists(_.hasImplicitBound)
    val implicitToken: Option[PsiElement] = parameters.toList.flatMap(_.clauses).flatMap(_.implicitToken).headOption
    (hasImplicitBound, implicitToken) match {
      case (true, Some(element)) =>
        val message = ScalaBundle.message("cannot.have.implicit.parameters.and.implicit.bounds")
        holder.createErrorAnnotation(element, message)
      case _ =>
    }
  }
}