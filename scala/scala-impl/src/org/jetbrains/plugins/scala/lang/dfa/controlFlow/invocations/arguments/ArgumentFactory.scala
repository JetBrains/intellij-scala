package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.SyntheticExpressionFactory.{wrapInSplatListExpression, wrapInTupleExpression}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByName, PassByValue, PassingMechanism, ProperArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

object ArgumentFactory {

  def buildArgumentsInEvaluationOrder(invocation: ImplicitArgumentsOwner, isTupledMethodInvocation: Boolean): Seq[Argument] = {
    val (matchedParams, maybeVarargArgument) = partitionNormalAndVarargArgs(invocation)

    if (isTupledMethodInvocation) List(buildTupledArgument(invocation.asInstanceOf[MethodInvocation]))
    else matchedParams
      .sortBy(ArgumentSorting.argumentPositionSortingKey)
      .map { case (arg, param) =>
        Argument(new ExpressionTransformer(arg), ProperArgument(param), getPassingMechanism(param))
      } :++ maybeVarargArgument
  }

  private def partitionNormalAndVarargArgs(invocation: ImplicitArgumentsOwner): (Seq[(ScExpression, Parameter)], Option[Argument]) = {
    // TODO the case with empty vararg list doesn't work - we need another way of knowing if this function has a vararg param,
    //  even if there are no arguments matching it, fix it once I handle multiple parameter lists
    // TODO implicit parameters don't work
    val maybeVarargParam = invocation.matchedParameters.map(_._2).find(_.psiParam.exists(_.isVarArgs))
    maybeVarargParam match {
      case Some(varargParam) =>
        val (argsMappedToVarargParam, normalArgs) = invocation.matchedParameters.reverse.partition(_._2 == varargParam)
        val varargArgument = buildSplatListArgument(argsMappedToVarargParam.map(_._1), varargParam)
        (normalArgs, Some(varargArgument))
      case None => (invocation.matchedParameters, None)
    }
  }

  private def buildTupledArgument(invocation: MethodInvocation): Argument = {
    implicit val projectContext: ProjectContext = invocation.getProject
    val tupleArgument = wrapInTupleExpression(invocation.argumentExpressions)
    val tupleParameter = Parameter(invocation.target.get.element match {
      case function: ScFunction => function.parameters.head
    })

    Argument(new ExpressionTransformer(tupleArgument), ProperArgument(tupleParameter),
      getPassingMechanism(tupleParameter))
  }

  private def buildSplatListArgument(varargContents: Seq[ScExpression], varargParam: Parameter): Argument = {
    implicit val projectContext: ProjectContext = varargContents.head.getProject
    val splatListArgument = wrapInSplatListExpression(varargContents)

    Argument(new ExpressionTransformer(splatListArgument), ProperArgument(varargParam),
      getPassingMechanism(varargParam))
  }

  private def getPassingMechanism(param: Parameter): PassingMechanism = {
    if (param.isByName) PassByName else PassByValue
  }
}
