package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScCharLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}

object StripMarginParser extends StringParserLike[ScStringLiteral] {

  override def parse(literal: ScStringLiteral): Option[Seq[StringPart]] = literal match {
    case WithStrippedMargin(_, marginChar) =>
      val res = ScStringLiteralParser.parse(literal, checkStripMargin = false)
      res.map(_.map(stripMargin(_, marginChar)))
    case _ =>
      None
  }

  private def stripMargin(part: StringPart, marginChar: Char): StringPart = part match {
    case Text(text) => Text(text.stripMargin(marginChar))
    case part       => part
  }
}

object WithStrippedMargin {

  def unapply(literal: ScStringLiteral): Option[(ScExpression, Char)] = literal.getParent match {
    case StripMarginCall(reference: ScReferenceExpression, _, Seq()) => Some(reference, '|')
    case Parent(StripMarginCall(mc: ScMethodCall, _, Seq(ScCharLiteral(value)))) =>Some(mc, value)
    case _ => None
  }

  private[format] object StripMarginCall {

    def unapply(expression: ScExpression): Option[(ScExpression, ScStringLiteral, Seq[ScExpression])] = expression match {
      case MethodRepr(itself, Some(literal: ScStringLiteral), Some(ref), args) if literal.isMultiLineString && ref.refName == "stripMargin" =>
        Some((itself, literal, args))
      case _ => None
    }
  }
}