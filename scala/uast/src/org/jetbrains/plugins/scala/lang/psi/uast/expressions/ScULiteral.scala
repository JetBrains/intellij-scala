package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{ULiteralExpression, ULiteralExpressionAdapter}

/**
  * [[ScLiteral]] adapter for the [[ULiteralExpression]]
  *
  * @param scExpression Scala PSI element representing literal expression
  */
class ScULiteral(override protected val scExpression: ScLiteral,
                 override protected val parent: LazyUElement)
    extends ULiteralExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  @Nullable
  override def getValue: AnyRef = scExpression.getValue

  @Nullable
  override def evaluate(): AnyRef = getValue
}
