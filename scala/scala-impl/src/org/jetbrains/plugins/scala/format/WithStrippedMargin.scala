package org.jetbrains.plugins.scala.format

import org.jetbrains.plugins.scala.extensions.{BooleanExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScCharLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

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
  def unapply(literal: ScStringLiteral): Option[(ScExpression, Char)] =
    literal.getParent match {
      case StripMarginCall(call, `literal`, char) => Some(call -> char)
      case _ => None
    }

  private[format] object StripMarginCall {
    def unapply(expression: ScExpression): Option[(ScExpression, ScStringLiteral, Char)] = {
      val (name, result) = expression match {
        case mc@ScMethodCall(reference@ScReferenceExpression.withQualifier(lit: ScStringLiteral), Seq(ScCharLiteral(char))) =>
          reference.refName -> (mc, lit, char)
        case Parent(mc@ScMethodCall(reference@ScReferenceExpression.withQualifier(lit: ScStringLiteral), Seq(ScCharLiteral(char)))) =>
          reference.refName -> (mc, lit, char)
        case reference@ScReferenceExpression.withQualifier(lit: ScStringLiteral) =>
          reference.refName -> (reference, lit, MultilineStringUtil.DefaultMarginChar)
        case postfix@ScPostfixExpr(lit: ScStringLiteral, op) =>
          op.refName -> (postfix, lit, MultilineStringUtil.DefaultMarginChar)
        case infix@ScInfixExpr(lit: ScStringLiteral, op, ScCharLiteral(char)) =>
          op.refName -> (infix, lit, char)
        case _ =>
          return None
      }

      (name == "stripMargin").option(result)
    }
  }
}
