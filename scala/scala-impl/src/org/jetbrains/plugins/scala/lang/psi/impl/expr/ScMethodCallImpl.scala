package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScMethodCallImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScMethodCall {

  def getInvokedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def argumentExpressions: Seq[ScExpression] = if (args != null) args.exprs else Nil

  override def getEffectiveInvokedExpr: ScExpression = {
    findChildByClassScala(classOf[ScExpression]) match {
      case x: ScParenthesisedExpr => x.expr.getOrElse(x)
      case x => x
    }
  }

  override def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = {
    updateExpression() match {
      case Some(expr) => argumentExpressions ++ Seq(expr)
      case _ => argumentExpressions
    }
  }

  override def toString: String = "MethodCall"
}