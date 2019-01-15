package org.jetbrains.plugins.scala
package lang.psi.api.base

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedPrefixReference, ScInterpolatedStringPartReference}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

import scala.collection.mutable.ListBuffer

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScalaPsiElement {
  def isMultiLineString: Boolean

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def getStringContextExpression: Option[ScExpression] = {
    val quote = if (isMultiLineString) "\"\"\"" else "\""
    val parts = getStringParts.mkString(quote, s"$quote, $quote", quote) //making list of string literals
    val params = getInjections.map(_.getText).mkString("(", ",", ")")
    if (getContext == null) None else Option(ScalaPsiElementFactory.createExpressionWithContextFromText(
      s"_root_.scala.StringContext($parts).${getFirstChild.getText}$params", getContext, ScInterpolated.this))
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
