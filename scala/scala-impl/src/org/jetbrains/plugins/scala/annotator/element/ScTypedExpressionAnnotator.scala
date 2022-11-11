package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, TypeDiff}
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Mismatch, asString}
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, TypePresentationContext}

object ScTypedExpressionAnnotator extends ElementAnnotator[ScTypedExpression] {

  override def annotate(element: ScTypedExpression, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      val isTypeAscriptionToFunctionLiteral = element.expr match {
        case ScParenthesisedExpr(_: ScFunctionExpr) | ScBlock(_: ScFunctionExpr) => true
        case _ => false
      }

      if (!isTypeAscriptionToFunctionLiteral) { // handled in ScFunctionExprAnnotator
        doAnnotate(element)
      }
    }
  }

  private[annotator] def doAnnotate(element: ScTypedExpression)(implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val context: TypePresentationContext = TypePresentationContext(element)
    element.typeElement.foreach(checkUpcasting(element.expr, _))
  }

  // SCL-15544
  private def checkUpcasting(expression: ScExpression, typeElement: ScTypeElement)
                            (implicit holder: ScalaAnnotationHolder, context: TypePresentationContext): Unit = {
    expression.getTypeAfterImplicitConversion().tr.foreach { actual =>
      val expected = typeElement.calcType

      if (!actual.conforms(expected)) {
        val ranges = mismatchRangesIn(typeElement, actual)
        // TODO add messange to the whole element, but higlight separate parts?
        // TODO fine-grained tooltip
        val wideActual = (expected, actual) match {
          case (_: ScLiteralType, t2: ScLiteralType) => t2
          case (_, t2: ScLiteralType) => t2.wideType
          case (_, t2) => t2
        }
        val message = ScalaBundle.message("cannot.upcast.type.to.other.type", wideActual.presentableText, expected.presentableText)
        ranges.foreach { range =>
          holder.createErrorAnnotation(range, message, ReportHighlightingErrorQuickFix)
        }
      }
    }
  }

  // SCL-15481
  def mismatchRangesIn(expected: ScTypeElement, actual: ScType)(implicit context: TypePresentationContext): Seq[TextRange] = {
    val diff = TypeDiff.forExpected(expected.calcType, actual)

    if (expected.textMatches(asString(diff))) { // make sure that presentations match
      val (ranges, _) =  diff.flatten.foldLeft((Seq.empty[TextRange], expected.getTextOffset)) { case ((acc, offset), x) =>
        val length = x.text.length
        (if (x.is[Mismatch]) TextRange.create(offset, offset + length) +: acc else acc, offset + length)
      }
      ranges
    } else {
      Seq(expected.getTextRange)
    }
  }
}
