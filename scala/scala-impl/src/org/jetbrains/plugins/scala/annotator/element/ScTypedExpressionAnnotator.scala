package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScTypedExpression}

object ScTypedExpressionAnnotator extends ElementAnnotator[ScTypedExpression] {
  override def annotate(element: ScTypedExpression, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (typeAware) {
      element.typeElement.foreach(checkUpcasting(element.expr, _, holder))
    }
  }

  // SCL-15544
  private def checkUpcasting(expression: ScExpression, typeElement: ScTypeElement, holder: AnnotationHolder): Unit = {
    expression.getTypeAfterImplicitConversion().tr.foreach { actualType =>
      val expectedType = typeElement.calcType

      if (!actualType.conforms(expectedType)) {
        val message = s"Cannot upcast ${actualType.presentableText} to ${expectedType.presentableText}"
        // TODO fine-grained ranges
        val annotation = holder.createErrorAnnotation(typeElement, message)
        // TODO fine-grained tooltip
        annotation.registerFix(ReportHighlightingErrorQuickFix)
      }
    }
  }
}
