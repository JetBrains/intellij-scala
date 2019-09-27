package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.internals.ResolveProcessor._
import org.jetbrains.uast._

/**
  * [[ScPrefixExpr]] adapter for the [[UPrefixExpression]]
  *
  * @param scExpression Scala PSI element representing prefix expression (e.g. `+1`)
  */
final class ScUPrefixExpression(
  override protected val scExpression: ScPrefixExpr,
  override protected val parent: LazyUElement
) extends UPrefixExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  // TODO: not implemented properly
  override def getOperator: UastPrefixOperator =
    new UastPrefixOperator(scExpression.operation.getText)

  override def getOperand: UExpression =
    scExpression.operand.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getOperatorIdentifier: UIdentifier =
    createUIdentifier(scExpression.operation, this)

  @Nullable
  override def resolveOperator(): PsiMethod =
    scExpression.operation.resolveTo[PsiMethod]()
}
