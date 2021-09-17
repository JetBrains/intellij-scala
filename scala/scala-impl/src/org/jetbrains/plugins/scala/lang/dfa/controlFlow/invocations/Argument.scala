package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{ArgumentKind, PassingMechanism}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.{ExpressionTransformer, Transformable, UnknownValueTransformer}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

case class Argument(content: Transformable, kind: ArgumentKind, passingMechanism: PassingMechanism)

object Argument {

  sealed trait ArgumentKind
  case class RegularArgument(parameterMapping: ScParameter) extends ArgumentKind
  case object ThisArgument extends ArgumentKind

  sealed trait PassingMechanism
  case object PassByValue extends PassingMechanism
  case object PassByName extends PassingMechanism

  def fromExpression(expression: Option[ScExpression], kind: ArgumentKind, passingMechanism: PassingMechanism): Argument = {
    val transformer = expression.map(new ExpressionTransformer(_)).getOrElse(new UnknownValueTransformer)
    Argument(transformer, kind, passingMechanism)
  }
}
