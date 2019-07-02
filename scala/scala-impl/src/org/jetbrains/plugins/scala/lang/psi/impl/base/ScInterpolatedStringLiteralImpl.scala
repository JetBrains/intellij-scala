package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.meta.intellij.QuasiquoteInferUtil._

final class ScInterpolatedStringLiteralImpl(node: ASTNode,
                                            override val toString: String)
  extends ScLiteralImpl(node, toString)
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

  protected override def innerType: TypeResult = getStringContextExpression match {
    case Some(mc: ScMethodCall) => mc.getInvokedExpr match {
      case expr: ScReferenceExpression if isMetaQQ(expr) =>
        getMetaQQExprType(this)
      case expr: ScReferenceExpression =>
        InterpolatedStringMacroTypeProvider.getTypeProvider(expr).fold(mc.getNonValueType()) {
          _.inferExpressionType(this)
        }
      case _ => mc.getNonValueType()
    }
    case Some(expr) => expr.getNonValueType()
    case _ => Failure(s"Cannot find method $referenceText of StringContext")
  }

  override def reference: Option[ScReferenceExpression] = getFirstChild match {
    case reference: ScReferenceExpression => Some(reference)
    case _ => None
  }

  override def referenceName: String = reference.fold("")(_.refName)

  override def isString: Boolean =
    getNode.getLastChildNode.getElementType == tINTERPOLATED_STRING_END

  override def isMultiLineString: Boolean = isString && {
    val next = referenceNode.getTreeNext
    next != null && next.getElementType == tINTERPOLATED_MULTILINE_STRING
  }

  override protected def startQuote: String = referenceText + super.startQuote

  override protected def endQuote: String = super.startQuote
}
