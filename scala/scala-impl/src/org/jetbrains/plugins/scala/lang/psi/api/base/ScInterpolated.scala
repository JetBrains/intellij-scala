package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedExpressionPrefix, ScInterpolatedPatternPrefix}

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
    child <- getNode.getChildren(null).toSeq
    part  = buildStringPart(child, dummy = false)
    if part != null
  } yield part

  def getStringPartsDummies: Seq[String] = for {
    child <- getNode.getChildren(null).toSeq
    part  = buildStringPart(child, dummy = true)
    if part != null
  } yield part

  // TODO: handle raw literal when dummy=false (in plain and multiline strings)
  @inline
  private def buildStringPart(child: ASTNode, dummy: Boolean): String =
    child.getElementType match {
      case `tINTERPOLATED_STRING` =>
        if (dummy) "" else child.getText.stripPrefix(SingleLineQuote)
      case `tINTERPOLATED_MULTILINE_STRING` =>
        if (dummy) "" else child.getText.stripPrefix(MultiLineQuote)
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
}

object ScInterpolated {

  implicit class InterpolatedExt(private val interpolated: ScInterpolated) extends AnyVal {

    def quoteLength: Int = (if (interpolated.isMultiLineString) MultiLineQuote else SingleLineQuote).length
  }
}
