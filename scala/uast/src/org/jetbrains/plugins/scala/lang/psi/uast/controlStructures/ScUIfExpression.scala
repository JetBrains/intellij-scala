package org.jetbrains.plugins.scala.lang.psi.uast.controlStructures

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIf
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

/**
  * [[ScIf]] adapter for the [[UIfExpression]]
  *
  * @param scExpression Scala PSI element representing `if [else]` expression
  */
class ScUIfExpression(override protected val scExpression: ScIf,
                      override protected val parent: LazyUElement)
    extends UIfExpressionAdapter
    with ScUExpression
    with ScUAnnotated {

  import scExpression._

  override def getCondition: UExpression =
    condition.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getElseExpression: UExpression =
    elseExpression.convertToUExpressionOrEmpty(this)

  @Nullable
  override def getElseIdentifier: UIdentifier =
    elseKeyword.map(createUIdentifier(_, this)).orNull

  override def getIfIdentifier: UIdentifier =
    createUIdentifier(
      scExpression.findFirstChildByType(ScalaTokenTypes.kIF),
      this
    )

  override def isTernary: Boolean = false

  @Nullable
  override def getThenExpression: UExpression =
    thenExpression.convertToUExpressionOrEmpty(this)
}
