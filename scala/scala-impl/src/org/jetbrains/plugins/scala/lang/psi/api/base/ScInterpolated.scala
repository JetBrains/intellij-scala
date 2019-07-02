package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedPrefixReference, ScInterpolatedStringPartReference}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScalaPsiElement {

  import QuotedLiteralImplBase._
  import lexer.ScalaTokenTypes._

  def isMultiLineString: Boolean

  def isString: Boolean

  protected final def referenceNode: ASTNode = getNode.getFirstChildNode

  protected final def referenceText: String = referenceNode.getText

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def getStringContextExpression: Option[ScExpression] = getContext match {
    case null => None
    case _ if !isString => None
    case context =>
      val quote = if (isMultiLineString) MultiLineQuote else SingleLineQuote

      val constructorParameters = getStringParts.map(quote + _ + quote)
        .commaSeparated(Model.Parentheses)
      val methodParameters = getInjections.map(_.getText)
        .commaSeparated(Model.Parentheses)

      val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(
        s"_root_.scala.StringContext$constructorParameters.$referenceText$methodParameters",
        context,
        this
      )
      Some(expression)
  }

  def getInjections: Seq[ScExpression] =
    getChildren.toSeq.filter {
      case _: ScBlockExpr => true
      case _: ScInterpolatedStringPartReference |
           _: ScInterpolatedPrefixReference => false
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
