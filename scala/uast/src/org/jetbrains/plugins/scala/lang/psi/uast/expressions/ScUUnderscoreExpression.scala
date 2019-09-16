package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.{UExpression, UExpressionAdapter}

/**
  * [[ScUnderscoreSection]] adapter for the [[UExpression]]
  *
  * @param scExpression Scala PSI element representing underscore section
  *                     expression (e.g. `_`)
  */
class ScUUnderscoreExpression(
  override protected val scExpression: ScUnderscoreSection,
  override protected val parent: LazyUElement
) extends UExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  override def asRenderString(): String = "_"
  override def asLogString(): String = "ScalaUnderscoreSection"
}
