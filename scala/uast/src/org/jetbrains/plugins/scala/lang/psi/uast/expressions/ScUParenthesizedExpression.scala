package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{
  UExpression,
  UParenthesizedExpression,
  UParenthesizedExpressionAdapter
}

/**
  * [[ScParenthesisedExpr]] adapter for the [[UParenthesizedExpression]]
  *
  * @param scExpression Scala PSI element representing parenthesized expression (e.g. `(4 + 2)`)
  */
class ScUParenthesizedExpression(
  override protected val scExpression: ScParenthesisedExpr,
  override protected val parent: LazyUElement
) extends UParenthesizedExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getExpression: UExpression =
    scExpression.innerElement.convertToUExpressionOrEmpty(this)
}
