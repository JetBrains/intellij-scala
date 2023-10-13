package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{ArgumentKind, PassingMechanism}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

final case class Argument(content: Option[ScExpression], kind: ArgumentKind, passingMechanism: PassingMechanism)

object Argument {

  sealed trait ArgumentKind
  case class ProperArgument(parameterMapping: Parameter) extends ArgumentKind
  case object ThisArgument extends ArgumentKind

  sealed trait PassingMechanism
  case object PassByValue extends PassingMechanism
  case object PassByName extends PassingMechanism

  def fromExpression(expression: Option[ScExpression],
                     kind: ArgumentKind,
                     passingMechanism: PassingMechanism): Argument = {
    Argument(expression, kind, passingMechanism)
  }

  def fromArgParamMapping(argParamMapping: (ScExpression, Parameter)): Argument = argParamMapping match {
    case (argExpression, param) =>
      Argument(Some(argExpression), ProperArgument(param), passingMechanism(param))
  }

  def passingMechanism(param: Parameter): PassingMechanism = {
    if (param.isByName) PassByName else PassByValue
  }
}
