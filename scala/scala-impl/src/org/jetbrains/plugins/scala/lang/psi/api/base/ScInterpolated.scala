package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedPrefixReference, ScInterpolatedStringPartReference}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

import scala.collection.mutable.ListBuffer

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScalaPsiElement {

  def isMultiLineString: Boolean

  def isString: Boolean

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def getStringContextExpression: Option[ScExpression] = getContext match {
    case null => None
    case _ if !isString => None
    case context =>
      val quote = this.quote
      val partsString = getStringParts.mkString(quote, s"$quote, $quote", quote) // making list of string literals

      val params = getInjections.map(_.getText).mkString("(", ",", ")")

      val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(
        s"_root_.scala.StringContext($partsString).${getFirstChild.getText}$params",
        context,
        ScInterpolated.this
      )
      Some(expression)
  }

  def getInjections: Seq[ScExpression] =
    getNode.getChildren(null).flatMap {
      _.getPsi match {
        case expression: ScBlockExpr => Some(expression)
        case _: ScInterpolatedStringPartReference |
             _: ScInterpolatedPrefixReference => None
        case expression: ScReferenceExpression => Some(expression)
        case _ => None
      }
    }

  def getStringParts: Seq[String] = {
    val childNodes = this.children.map(_.getNode)
    val result = ListBuffer[String]()
    val emptyString = ""
    for {
      child <- childNodes
    } {
      child.getElementType match {
        case ScalaTokenTypes.tINTERPOLATED_STRING =>
          child.getText.headOption match {
            case Some('"') => result += child.getText.substring(1)
            case Some(_) => result += child.getText
            case None => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
          child.getText match {
            case s if s.startsWith("\"\"\"") => result += s.substring(3)
            case s: String => result += s
            case _ => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION | ScalaTokenTypes.tINTERPOLATED_STRING_END =>
          val prev = child.getTreePrev
          if (prev != null) prev.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_STRING =>
            case _ => result += emptyString //insert empty string between injections
          }
        case _ =>
      }
    }
    result.toVector
  }
}

object ScInterpolated {

  implicit class InterpolatedExt(private val interpolated: ScInterpolated) extends AnyVal {

    def quote: String = if (interpolated.isMultiLineString) MultiLineQuote else SingleLineQuote
  }
}
