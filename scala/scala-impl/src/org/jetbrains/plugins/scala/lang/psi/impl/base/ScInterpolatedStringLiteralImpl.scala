package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.{InterpolatedStringType, ScInterpolatedStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.meta.intellij.QuasiquoteInferUtil

/**
  * User: Dmitry Naydanov
  * Date: 3/17/12
  */
final class ScInterpolatedStringLiteralImpl(node: ASTNode)
  extends ScLiteralImpl(node) with ScInterpolatedStringLiteral {

  import InterpolatedStringType._
  import ScLiteralImpl._

  override def getType: StringType = getNode.getFirstChildNode.getText match {
    case "s" => STANDART
    case "f" => FORMAT
    case "id" => PATTERN
    case "raw" => RAW
    case _ => null
  }

  protected override def innerType: TypeResult = getStringContextExpression match {
    case Some(mc: ScMethodCall) => mc.getInvokedExpr match {
      case expr: ScReferenceExpression if QuasiquoteInferUtil.isMetaQQ(expr) =>
        QuasiquoteInferUtil.getMetaQQExprType(this)
      case expr: ScReferenceExpression =>
        InterpolatedStringMacroTypeProvider.getTypeProvider(expr) match {
          case Some(typeProvider) => typeProvider.inferExpressionType(this)
          case None => mc.getNonValueType()
        }
      case _ => mc.getNonValueType()
    }
    case Some(expr) => expr.getNonValueType()
    case _ => Failure(s"Cannot find method ${getFirstChild.getText} of StringContext")
  }

  override def reference: Option[ScReferenceExpression] = getFirstChild match {
    case ref: ScReferenceExpression => Some(ref)
    case _ => None
  }

  override def isMultiLineString: Boolean = getText.endsWith(MultiLineQuote)

  override def isString: Boolean = true

  override def getValue: AnyRef = findChildByClassScala(classOf[ScLiteralImpl]) match {
    case literal: ScLiteralImpl => literal.getValue
    case _ => ""
  }

  override def contentRange: TextRange = {
    val prefixLength = reference.map(_.refName)
      .fold(0)(_.length)

    val Some((startShift, endShift)) = stringShifts(
      if (isMultiLineString) MultiLineQuote else SingleLineQuote
    )

    val range = getTextRange
    new TextRange(
      range.getStartOffset + prefixLength + startShift,
      range.getEndOffset - endShift
    )
  }
}
