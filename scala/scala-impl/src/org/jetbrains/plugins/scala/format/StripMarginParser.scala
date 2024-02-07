package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScCharLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

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

  /**
   * Detects if string literal is followed by a `.stripMargin` call.<br>
   * Example: {{{
   *   //dot-notation
   *   """123""".stripMargin
   *   """123""".stripMargin('#')
   *
   *   //infix-notation
   *   """123""" stripMargin
   *   """123""" stripMargin '#'
   * }}}
 *
   * @return pair:
   *         1. parent expression containing the original string literal and stripMargin call
   *         2. margin character
   */
  def unapply(literal: ScStringLiteral): Option[(ScExpression, Char)] = {
    val literalParent = literal.getParent
    literalParent match {
      case StripMarginCall(reference: ScReferenceExpression, _, Seq()) => //detect `"""123""".stripMargin`
        Some(reference, MultilineStringUtil.DefaultMarginChar)
      case StripMarginCall(postfix: ScPostfixExpr, _, Seq()) => //detect `"""123""" stripMargin`
        Some(postfix, MultilineStringUtil.DefaultMarginChar)
      case StripMarginCall(infix: ScInfixExpr, _, Seq(ScCharLiteral(value))) => //detect `"""123""" stripMargin '#'`
        Some(infix, value)
      case Parent(StripMarginCall(mc: ScMethodCall, _, Seq(ScCharLiteral(value)))) => //detect `"""123""".stripMargin('#')`
        Some(mc, value)
      case _ =>
        None
    }
  }

  private[format] object StripMarginCall {

    def unapply(expression: ScExpression): Option[(ScExpression, ScStringLiteral, Seq[ScExpression])] = expression match {
      case MethodRepr(itself, Some(literal: ScStringLiteral), Some(ref), args) if literal.isMultiLineString && ref.refName == "stripMargin" =>
        Some((itself, literal, args))
      case _ => None
    }
  }
}