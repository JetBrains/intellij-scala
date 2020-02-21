package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.{ASTNode, LanguageNamesValidation}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.StringContextCanonical

import scala.meta.intellij.QuasiquoteInferUtil._

final class ScInterpolatedStringLiteralImpl(node: ASTNode,
                                            override val toString: String)
  extends ScStringLiteralImpl(node, toString)
    with ScInterpolatedStringLiteral {

  import ScInterpolatedStringLiteral._
  import lang.lexer.ScalaTokenTypes._

  override def kind: Kind = referenceText match {
    case "s" => Standard
    case "f" => Format
    case "id" => Pattern
    case "raw" => Raw
    case _ => null
  }

  protected override def innerType: TypeResult =
    desugaredExpression.fold(Failure(s"Cannot find method $referenceText of StringContext"): TypeResult) {
      case (reference, _) if isMetaQQ(reference) =>
        getMetaQQExprType(this)
      case (reference, call) =>
        InterpolatedStringMacroTypeProvider.getTypeProvider(reference)
          .fold(call.getNonValueType()) {
            _.inferExpressionType(this)
          }
    }

  override def reference: Option[ScReferenceExpression] = getFirstChild match {
    case reference: ScReferenceExpression => Some(reference)
    case _ => None
  }

  override def referenceName: String = reference.fold("")(_.refName)

  override def isString: Boolean =
    getNode.getLastChildNode.getElementType == tINTERPOLATED_STRING_END

  override def isMultiLineString: Boolean = isString && {
    val next = firstNode.getTreeNext
    next != null && next.getElementType == tINTERPOLATED_MULTILINE_STRING
  }

  override protected def startQuote: String = referenceText + super.startQuote

  override protected def endQuote: String = super.startQuote

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  override def desugaredExpression: Option[(ScReferenceExpression, ScMethodCall)] = (referenceText, getContext) match {
    case (methodName, context) if context != null &&
      isString &&
      isValidIdentifier(methodName) =>
      val quote = endQuote

      val constructorParameters = getStringParts.map(quote + _ + quote)
        .commaSeparated(Model.Parentheses)

      val methodParameters = getInjections.map { injection =>
        injection -> injection.getText
      }.map {
        case (_: ScReferenceExpression, text) if !isValidIdentifier(text) => "???"
        case (_, text) => text
      }.commaSeparated(Model.Parentheses)

      val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(
        s"$StringContextCanonical$constructorParameters.$methodName$methodParameters",
        context,
        this
      ).asInstanceOf[ScMethodCall]
      Some(expression.getInvokedExpr.asInstanceOf[ScReferenceExpression], expression)
    case _ => None
  }

  private def referenceText: String = firstNode.getText

  private def isValidIdentifier(name: String) = {
    val validation = LanguageNamesValidation.INSTANCE.forLanguage(getLanguage)
    val project = getProject

    !validation.isKeyword(name, project) &&
      validation.isIdentifier(name, project)
  }
}
