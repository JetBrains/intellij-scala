package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassByName, PassByValue, PassingMechanism, ProperArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

object ArgumentUtils {

  // TODO reverse it once it works more or less: param -> arg
  /**
   * Maps the argument on position [[argIndex]] in the argument evaluation order of a function's invocation
   * to the parameter on position [[paramIndex]] in this function's parameter list.
   */
  final case class ArgParamMapping(argIndex: Int, paramIndex: Int)

  // TODO spot and handle right associativity and other such cases
  def buildArgumentsInEvaluationOrder(argsMappedToParams: Seq[(ScExpression, Parameter)]): Seq[Argument] = {
    argsMappedToParams
      .sortBy(ArgumentSorting.argumentPositionSortingKey)
      .map { case (arg, param) =>
        Argument(new ExpressionTransformer(arg), ProperArgument(param), getPassingMechanism(param))
      }
  }

  private def getPassingMechanism(param: Parameter): PassingMechanism = {
    if (param.isByName) PassByName else PassByValue
  }

  // TODO further mapping and auto-tupling

  //  def buildMapping: Seq[ArgParamMapping] = {
  //    if (isTupled) Seq(ArgParamMapping(0, 0))
  //    else argsMappedToParams.zipWithIndex.map { case ((_, param), index) =>
  //      ArgParamMapping(argIndex = index, paramIndex = param.index)
  //    }
  //  }

  private object ArgumentSorting {

    def argumentPositionSortingKey(matchedParameter: (ScExpression, Parameter)): (Int, Int) = {
      val (expression, param) = matchedParameter
      // Actually supplied arguments have to be evaluated before default parameters
      val notDefault = expression.parent.exists(!_.is[ScParameter])
      if (notDefault) (0, expression.getTextOffset)
      else (1, param.index)
    }
  }
}
