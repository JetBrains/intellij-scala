package org.jetbrains.plugins.scala.lang.psi.uast.controlStructures

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScWhile
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUExpression
}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

/**
  * [[ScWhile]] adapter for the [[UWhileExpression]]
  *
  * @param scExpression Scala PSI element representing `while() {}` cycle
  */
final class ScUWhileExpression(override protected val scExpression: ScWhile,
                               override protected val parent: LazyUElement)
    extends UWhileExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  import scExpression._

  override def getCondition: UExpression =
    condition.convertToUExpressionOrEmpty(this)

  override def getWhileIdentifier: UIdentifier =
    createUIdentifier(
      scExpression.findFirstChildByType(ScalaTokenTypes.kWHILE),
      this
    )

  override def getBody: UExpression =
    expression.convertToUExpressionOrEmpty(this)
}
