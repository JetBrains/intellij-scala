package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import api.expr._
import psi.ScalaPsiElementImpl
import types.result.{Failure, TypingContext}
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
* Time: 9:24:19
*/

class ScParenthesisedExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParenthesisedExpr {
  override def toString: String = "ExpressionInParenthesis"

  protected override def innerType(ctx: TypingContext) = {
    expr match {
      case Some(x: ScExpression) =>
        val res = x.getNonValueType(ctx)
        res
      case _ => Failure("No expression in parentheseses", Some(this))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitExprInParent(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitExprInParent(this)
      case _ => super.accept(visitor)
    }
  }

}