package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.internals.ResolveProcessor._
import org.jetbrains.uast._

/**
  * [[ScInfixExpr]] adapter for the [[UBinaryExpression]]
  *
  * @param scExpression Scala PSI element representing binary expression (e.g. `4 + 2`)
  */
final class ScUBinaryExpression(
  override protected val scExpression: ScInfixExpr,
  override protected val parent: LazyUElement
) extends UBinaryExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getLeftOperand: UExpression =
    scExpression.left.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getOperatorIdentifier: UIdentifier =
    createUIdentifier(scExpression.operation, this)

  override def getRightOperand: UExpression =
    scExpression.right.convertToUExpressionOrEmpty(this)

  @Nullable
  override def resolveOperator(): PsiMethod =
    scExpression.operation.resolveTo[PsiMethod]()

  // TODO: not implemented properly
  override def getOperator: UastBinaryOperator =
    new UastBinaryOperator(scExpression.operation.getText)
}

/**
  * [[ScAssignment]] adapter for the [[UBinaryExpression]]
  *
  * @param scExpression Scala PSI element representing assignment (e.g. `a = 2`)
  */
class ScUAssignment(override protected val scExpression: ScAssignment,
                    override protected val parent: LazyUElement)
    extends UBinaryExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getLeftOperand: UExpression =
    scExpression.leftExpression.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getOperatorIdentifier: UIdentifier =
    createUIdentifier(scExpression.assignNavigationElement, this)

  override def getRightOperand: UExpression =
    scExpression.rightExpression.convertToUExpressionOrEmpty(this)

  @Nullable
  override def resolveOperator(): PsiMethod = null

  override def getOperator: UastBinaryOperator = UastBinaryOperator.ASSIGN
}
