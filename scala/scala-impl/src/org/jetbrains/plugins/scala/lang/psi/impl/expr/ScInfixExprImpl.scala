package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScInfixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScInfixExpr {

  override def argumentExpressions: Seq[ScExpression] = {
    if (isRightAssoc) Seq(lOp)
    else rOp match {
      case tuple: ScTuple => tuple.exprs
      case t: ScParenthesisedExpr => t.expr match {
        case Some(expr) => Seq(expr)
        case None => Seq(t)
      }
      case _: ScUnitExpr => Seq.empty
      case expr => Seq(expr)
    }
  }

  protected override def innerType: TypeResult = {
    operation.bind() match {
      //this is assignment statement: x += 1 equals to x = x + 1
      case Some(r) if r.element.name + "=" == operation.refName =>
        super.innerType
        val lText = lOp.getText
        val rText = rOp.getText
        val exprText = s"$lText = $lText ${r.element.name} $rText"
        val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, getContext, this)
        newExpr.`type`()
      case _ => super.innerType
    }
  }

  override def toString: String = "InfixExpression"
}
