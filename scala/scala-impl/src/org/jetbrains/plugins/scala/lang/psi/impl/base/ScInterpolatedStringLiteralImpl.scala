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
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes

final class ScInterpolatedStringLiteralImpl(node: ASTNode,
                                            override val toString: String)
  extends ScLiteralImpl(node, toString) with ScInterpolatedStringLiteral {

  import InterpolatedStringType._
  import ScLiteralImpl._

  import scala.meta.intellij.QuasiquoteInferUtil._

  override def getType: StringType = literalNode.getText match {
    case "s" => STANDARD
    case "f" => FORMAT
    case "id" => PATTERN
    case "raw" => RAW
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
    case _ => Failure(s"Cannot find method ${getFirstChild.getText} of StringContext")
  }

  override def reference: Option[ScReferenceExpression] = getFirstChild match {
    case reference: ScReferenceExpression => Some(reference)
    case _ => None
  }

  override def referenceName: String = reference.fold("")(_.refName)

  override def isMultiLineString: Boolean = getText.endsWith(MultilineQuotes)

  override def isString: Boolean = true

  override def getValue: AnyRef = findChildByClassScala(classOf[ScLiteralImpl]) match {
    // FIXME: it is actually always "" because child with type ScLiteralImpl can't be found...
    case literal: ScLiteralImpl => literal.getValue
    case _ => ""
  }

  override def contentRange: TextRange = {
    val Some((startShift, endShift)) = stringShifts(
      if (isMultiLineString) MultiLineQuote else SingleLineQuote
    )

    val range = getTextRange
    new TextRange(
      range.getStartOffset + referenceName.length + startShift,
      range.getEndOffset - endShift
    )
  }
}
