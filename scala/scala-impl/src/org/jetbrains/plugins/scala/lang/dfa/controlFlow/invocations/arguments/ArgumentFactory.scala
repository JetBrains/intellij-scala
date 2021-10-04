package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments

import org.jetbrains.plugins.scala.lang.dfa.utils.SyntheticExpressionFactory.{wrapInSplatListExpression, wrapInTupleExpression}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

object ArgumentFactory {

  def buildArgumentsInEvaluationOrder(matchedParameters: Seq[(ScExpression, Parameter)],
                                      invocation: ImplicitArgumentsOwner,
                                      isTupled: Boolean): Seq[Argument] = {
    val (matchedParams, maybeVarargArgument) = partitionNormalAndVarargArgs(matchedParameters)
    invocation match {
      case methodInvocation: MethodInvocation if isTupled => List(buildTupledArgument(methodInvocation))
      case _ => matchedParams
        .sortBy(ArgumentSorting.argumentPositionSortingKey)
        .map(Argument.fromArgParamMapping) :++ maybeVarargArgument
    }
  }

  private def partitionNormalAndVarargArgs(matchedParameters: Seq[(ScExpression, Parameter)]): (Seq[(ScExpression, Parameter)], Option[Argument]) = {
    // TODO the case with empty vararg list doesn't work - we need another way of knowing if this function has a vararg param,
    //  even if there are no arguments matching it, fix it once I handle multiple parameter lists
    // TODO implicit parameters don't work properly, left for later
    val maybeVarargParam = matchedParameters.map(_._2).find(_.psiParam.exists(_.isVarArgs))
    maybeVarargParam match {
      case Some(varargParam) =>
        val (argsMappedToVarargParam, normalArgs) = matchedParameters.reverse.partition(_._2 == varargParam)
        val varargArgument = buildSplatListArgument(argsMappedToVarargParam.map(_._1), varargParam)
        (normalArgs, Some(varargArgument))
      case None => (matchedParameters, None)
    }
  }

  private def buildTupledArgument(invocation: MethodInvocation): Argument = {
    implicit val projectContext: ProjectContext = invocation.getProject
    val tupleArgument = wrapInTupleExpression(invocation.argumentExpressions)
    val tupleParameter = Parameter(invocation.target.get.element match {
      case function: ScFunction => function.parameters.head
    })

    Argument.fromArgParamMapping((tupleArgument, tupleParameter))
  }

  private def buildSplatListArgument(varargContents: Seq[ScExpression], varargParam: Parameter): Argument = {
    implicit val projectContext: ProjectContext = varargContents.head.getProject
    val splatListArgument = wrapInSplatListExpression(varargContents)

    Argument.fromArgParamMapping((splatListArgument, varargParam))
  }
}
