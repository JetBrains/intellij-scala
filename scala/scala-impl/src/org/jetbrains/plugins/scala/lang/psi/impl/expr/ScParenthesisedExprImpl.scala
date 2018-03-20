package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  *         Time: 9:24:19
  */
class ScParenthesisedExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScParenthesisedExpr {

  protected override def innerType: TypeResult = {
    innerElement match {
      case Some(x: ScExpression) =>
        val res = x.getNonValueType()
        res
      case _ => Failure("No expression in parentheseses")
    }
  }

  override def toString: String = "ExpressionInParenthesis"
}