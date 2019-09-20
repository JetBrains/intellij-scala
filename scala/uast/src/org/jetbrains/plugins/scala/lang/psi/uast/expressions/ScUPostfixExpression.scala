package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.{LazyUElement, ResolveCommon}
import org.jetbrains.uast._

/**
  * [[ScPostfixExpr]] adapter for the [[UPostfixExpression]]
  *
  * @param scExpression Scala PSI element representing postfix expression (e.g. `42 abs`)
  */
class ScUPostfixExpression(
  override protected val scExpression: ScPostfixExpr,
  override protected val parent: LazyUElement
) extends UPostfixExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getOperator: UastPostfixOperator =
    new UastPostfixOperator(scExpression.operation.getText)

  override def getOperand: UExpression =
    scExpression.operand.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getOperatorIdentifier: UIdentifier =
    createUIdentifier(scExpression.operation, this)

  @Nullable
  override def resolveOperator(): PsiMethod =
    ResolveCommon.resolveNullable[PsiMethod](scExpression.operation)
}
