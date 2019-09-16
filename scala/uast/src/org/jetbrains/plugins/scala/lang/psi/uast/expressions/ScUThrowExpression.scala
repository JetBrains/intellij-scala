package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScThrow
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{
  UExpression,
  UThrowExpression,
  UThrowExpressionAdapter
}

/**
  * [[ScThrow]] adapter for the [[UThrowExpression]]
  *
  * @param scExpression Scala PSI element representing `throw` expression
  */
class ScUThrowExpression(override protected val scExpression: ScThrow,
                         override protected val parent: LazyUElement)
    extends UThrowExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def getThrownExpression: UExpression =
    scExpression.expression.convertToUExpressionOrEmpty(this)
}
