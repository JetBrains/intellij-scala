package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.extensions.{Both, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScExpression, ScReferenceExpression}

/**
 * Nikolay.Tropin
 * 3/6/14
 */
object StripMarginParser extends StringParser {

  override def parse(element: PsiElement): Option[Seq[StringPart]] = Some(element) collect {
    case Both(lit: ScInterpolatedStringLiteral, WithStrippedMargin(_, marginChar)) =>
      val parts = InterpolatedStringParser.parse(lit, checkStripMargin = false).getOrElse(return None)
      parts.map {
        case Text(s) =>
          val stripped = s.stripMargin(marginChar)
          Text(stripped)
        case part => part
      }
    case Both(lit: ScLiteral, WithStrippedMargin(_, marginChar)) =>
      List(Text(lit.getValue.toString.stripMargin(marginChar)))
  }

}

object WithStrippedMargin {
  val STRIP_MARGIN = "stripMargin"

  def unapply(literal: ScLiteral): Option[(ScExpression, Char)] = {
    literal.getParent match {
      case MethodRepr(refExpr: ScReferenceExpression, Some(lit: ScLiteral), Some(ref), Nil)
        if lit.isMultiLineString && ref.refName == STRIP_MARGIN => Some(refExpr, '|')
      case _ childOf (MethodRepr(mc: ScMethodCall, Some(lit: ScLiteral), Some(ref), List(argLit: ScLiteral)))
        if lit.isMultiLineString && ref.refName == STRIP_MARGIN &&
                argLit.getFirstChild.getNode.getElementType == ScalaTokenTypes.tCHAR => Some(mc, argLit.getValue.asInstanceOf[Char])
      case _ => None
    }
  }

}

object IsStripMargin {
  val STRIP_MARGIN = WithStrippedMargin.STRIP_MARGIN

  def unapply(expr: ScExpression): Option[(ScLiteral, Char)] = {
    expr match {
      case MethodRepr(_, Some(lit: ScLiteral), Some(ref), args) if lit.isMultiLineString && ref.refName == STRIP_MARGIN =>
        val marginChar = args match {
          case Nil => '|'
          case Seq(argLit: ScLiteral) if argLit.getFirstChild.getNode.getElementType == ScalaTokenTypes.tCHAR => argLit.getValue.asInstanceOf[Char]
          case _ => '|'
        }
        Some(lit, marginChar)
      case _ => None
    }
  }
}
