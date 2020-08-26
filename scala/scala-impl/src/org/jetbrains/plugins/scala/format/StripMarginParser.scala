package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScCharLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}

/**
 * Nikolay.Tropin
 * 3/6/14
 */
object StripMarginParser extends StringParser {

  override def parse(element: PsiElement): Option[Seq[StringPart]] = element match {
    case literal@WithStrippedMargin(_, marginChar) =>
      def escapePercent(text: String) =
        Text(text.stripMargin(marginChar)).withEscapedPercent(element)

      literal match {
        case _: ScInterpolatedStringLiteral =>
          InterpolatedStringParser.parse(literal, checkStripMargin = false).map { parts =>
            parts.flatMap {
              case Text(text) => escapePercent(text)
              case part => part :: Nil
            }
          }
        case _ => Option(literal.getValue).map(_.toString).map(escapePercent)
      }
    case _ => None
  }

}

object WithStrippedMargin {

  def unapply(literal: ScLiteral): Option[(ScExpression, Char)] = literal.getParent match {
    case StripMarginCall(reference: ScReferenceExpression, _, Seq()) => Some(reference, '|')
    case Parent(StripMarginCall(mc: ScMethodCall, _, Seq(ScCharLiteral(value)))) =>Some(mc, value)
    case _ => None
  }

  private[format] object StripMarginCall {

    def unapply(expression: ScExpression): Option[(ScExpression, ScLiteral, collection.Seq[ScExpression])] = expression match {
      case MethodRepr(itself, Some(literal: ScLiteral), Some(ref), args) if literal.isMultiLineString && ref.refName == "stripMargin" =>
        Some((itself, literal, args))
      case _ => None
    }
  }
}