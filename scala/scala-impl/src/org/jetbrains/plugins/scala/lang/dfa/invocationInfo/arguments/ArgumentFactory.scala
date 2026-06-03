package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.TransformationFailedException
import org.jetbrains.plugins.scala.lang.dfa.utils.SyntheticExpressionFactory.{wrapInSplatListExpression, wrapInTupleExpression}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment, ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

object ArgumentFactory {

  val ArgumentCountLimit = 10

  def buildAllArguments(matchedArguments: Seq[Seq[(ScExpression, Parameter)]], argumentExpressions: Seq[Seq[ScExpression]],
                        invocation: ImplicitArgumentsOwner, isTupled: Boolean): List[List[Argument]] = {
    implicit val context: ProjectContext = invocation.getProject
    // There might be more arguments than the method requires. In this case, we should still evaluate all of the arguments.
    val fixedArgs = argumentExpressions.zip(matchedArguments).map {
      case (expressions, argParams) => fixUnmatchedArguments(expressions, argParams)
    }

    fixedArgs.map(buildArgumentsInEvaluationOrder(_, invocation, isTupled)).toList
  }

  def insertThisArgToArgList(invocation: MethodInvocation, properArgs: List[Argument],
                             thisArgument: Argument): List[Argument] = invocation match {
    case infixExpression: ScInfixExpr if infixExpression.isRightAssoc =>
      properArgs.head :: thisArgument :: properArgs.tail
    case _ => thisArgument :: properArgs
  }

  private def fixUnmatchedArguments(args: Seq[ScExpression], matchedArgs: Seq[(ScExpression, Parameter)])
                                   (implicit context: ProjectContext): Seq[(ScExpression, Parameter)] = {
    val notMatchedArgs = args.filter {
      case ScAssignment(_, Some(actualArg)) => isNotAlreadyMatched(matchedArgs, actualArg)
      case argument => isNotAlreadyMatched(matchedArgs, argument)
    }

    matchedArgs ++ buildFakeParameters(notMatchedArgs, matchedArgs.length)
  }

  private def isNotAlreadyMatched(matchedArgs: Seq[(ScExpression, Parameter)], arg: ScExpression): Boolean = {
    !matchedArgs.exists(_._1 == arg)
  }

  private def buildFakeParameters(args: Seq[ScExpression], initialIndex: Int)
                                 (implicit context: ProjectContext): Seq[(ScExpression, Parameter)] = {
    for ((argument, index) <- args.zipWithIndex)
      yield argument -> Parameter(api.Any, isRepeated = false, index = index + initialIndex)
  }

  private def buildArgumentsInEvaluationOrder(matchedParameters: Seq[(ScExpression, Parameter)],
                                              invocation: ImplicitArgumentsOwner,
                                              isTupled: Boolean): List[Argument] = {
    implicit val context: ProjectContext = invocation.getProject
    val (matchedParams, maybeVarargArgument) = partitionNormalAndVarargArgs(matchedParameters)
    invocation match {
      case methodInvocation: MethodInvocation if isTupled => List(buildTupledArgument(methodInvocation))
      case _ => matchedParams
        .sortBy(ArgumentSorting.argumentPositionSortingKey)
        .map(Argument.fromArgParamMapping)
        .toList :++ maybeVarargArgument
    }
  }

  private def partitionNormalAndVarargArgs(matchedParameters: Seq[(ScExpression, Parameter)])
                                          (implicit context: ProjectContext): (Seq[(ScExpression, Parameter)], Option[Argument]) = {
    val maybeVarargParam = matchedParameters.map(_._2).find(_.psiParam.exists(_.isVarArgs))
    maybeVarargParam match {
      case Some(varargParam) =>
        val (argsMappedToVarargParam, normalArgs) = matchedParameters.partition(_._2 == varargParam)
        val varargArgument = buildSplatListArgument(argsMappedToVarargParam.map(_._1), varargParam)
        (normalArgs, Some(varargArgument))
      case _ => (matchedParameters, None)
    }
  }

  private def buildTupledArgument(invocation: MethodInvocation): Argument = {
    implicit val context: ProjectContext = invocation.getProject
    val tupleArgument = wrapInTupleExpression(invocation.argumentExpressions)
    val tupleParameter = Parameter(invocation.target.get.element match {
      case function: PsiMethod => function.getParameterList.getParameter(0)
      case _ => throw TransformationFailedException(invocation, "Auto-tupling used not on something other than ScFunction")
    })

    Argument.fromArgParamMapping((tupleArgument, tupleParameter))
  }

  private def buildSplatListArgument(varargContents: Seq[ScExpression], varargParam: Parameter)
                                    (implicit context: ProjectContext): Argument = {
    val splatListArgument = wrapInSplatListExpression(varargContents)
    Argument.fromArgParamMapping((splatListArgument, varargParam))
  }
}
