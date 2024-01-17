package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments

import com.intellij.codeInsight.Nullability
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{ArgumentKind, PassingMechanism}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

final case class Argument(content: Option[ScExpression],
                          kind: ArgumentKind,
                          passingMechanism: PassingMechanism,
                          nullability: Nullability)

object Argument {

  sealed trait ArgumentKind
  case class ProperArgument(parameterMapping: Parameter) extends ArgumentKind
  case object ThisArgument extends ArgumentKind

  sealed trait PassingMechanism
  case object PassByValue extends PassingMechanism
  case object PassByName extends PassingMechanism

  def fromExpression(expression: Option[ScExpression],
                     kind: ArgumentKind,
                     passingMechanism: PassingMechanism,
                     nullability: Nullability): Argument = {
    Argument(expression, kind, passingMechanism, nullability)
  }

  def thisArg(expression: Option[ScExpression]): Argument =
    Argument(expression, ThisArgument, PassByValue, Nullability.NOT_NULL)

  def fromArgParamMapping(argParamMapping: (ScExpression, Parameter)): Argument = argParamMapping match {
    case (argExpression, param) =>
      Argument(Some(argExpression), ProperArgument(param), passingMechanism(param), ScalaDfaTypeUtils.nullability(param))
  }

  private def passingMechanism(param: Parameter): PassingMechanism =
    if (param.isByName) PassByName else PassByValue
}
