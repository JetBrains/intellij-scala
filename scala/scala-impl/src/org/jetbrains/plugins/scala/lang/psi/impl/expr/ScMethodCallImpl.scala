package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScMethodCallImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScMethodCall {

  override def thisExpr: Option[ScExpression] =
    getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression].flatMap { invokedExpr =>
      if (isApplyOrUpdateCall) Option(invokedExpr)
      else                     invokedExpr.qualifier
    }

  override def getInvokedExpr: ScExpression = findChild[ScExpression].get

  override def argumentExpressions: Seq[ScExpression] = args.exprs

  override def getEffectiveInvokedExpr: ScExpression =
    getInvokedExpr match {
      case x: ScParenthesisedExpr => x.innerElement.getOrElse(x)
      case x => x
    }

  override def toString: String = "MethodCall"
}
