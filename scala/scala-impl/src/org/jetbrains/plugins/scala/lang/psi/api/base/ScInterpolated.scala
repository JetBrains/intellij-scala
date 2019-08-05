package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedExpressionPrefix, ScInterpolatedPatternPrefix}

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScalaPsiElement {

  import lexer.ScalaTokenTypes._

  def isMultiLineString: Boolean

  def getInjections: Seq[ScExpression] =
    getChildren.toSeq.filter {
      case _: ScBlockExpr => true
      case _: ScInterpolatedExpressionPrefix |
           _: ScInterpolatedPatternPrefix => false
      case _: ScReferenceExpression => true
      case _ => false
    }.asInstanceOf[Seq[ScExpression]]

  def getStringParts: Seq[String] = for {
    child <- getNode.getChildren(null)

    part = child.getElementType match {
      case `tINTERPOLATED_STRING` => child.getText.stripPrefix(SingleLineQuote)
      case `tINTERPOLATED_MULTILINE_STRING` => child.getText.stripPrefix(MultiLineQuote)
      case `tINTERPOLATED_STRING_INJECTION` | `tINTERPOLATED_STRING_END` =>
        child.getTreePrev match {
          case null => null
          case prev =>
            prev.getElementType match {
              case `tINTERPOLATED_MULTILINE_STRING` | `tINTERPOLATED_STRING` => null
              case _ => "" //insert empty string between injections
            }
        }
      case _ => null
    }
    if part != null
  } yield part
}

object ScInterpolated {

  implicit class InterpolatedExt(private val interpolated: ScInterpolated) extends AnyVal {

    def quoteLength: Int = (if (interpolated.isMultiLineString) MultiLineQuote else SingleLineQuote).length
  }
}
