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
class ScMethodCallImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScMethodCall {

  override def getInvokedExpr: ScExpression = findChild[ScExpression].get

  override def argumentExpressions: Seq[ScExpression] = args.exprs

  override def getEffectiveInvokedExpr: ScExpression =
    getInvokedExpr match {
      case x: ScParenthesisedExpr => x.innerElement.getOrElse(x)
      case x                      => x
    }

  override def toString: String = "MethodCall"
}