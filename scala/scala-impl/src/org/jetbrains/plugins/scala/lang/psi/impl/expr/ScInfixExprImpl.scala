package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.collection.Seq

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr {
  override def toString: String = "InfixExpression"

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

  protected override def innerType: TypeResult[ScType] = {
    operation.bind() match {
      //this is assignment statement: x += 1 equals to x = x + 1
      case Some(r) if r.element.name + "=" == operation.refName =>
        super.innerType
        val lText = lOp.getText
        val rText = rOp.getText
        val exprText = s"$lText = $lText ${r.element.name} $rText"
        val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, getContext, this)
        newExpr.getType()
      case _ => super.innerType
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitInfixExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitInfixExpression(this)
      case _ => super.accept(visitor)
    }
  }
}
