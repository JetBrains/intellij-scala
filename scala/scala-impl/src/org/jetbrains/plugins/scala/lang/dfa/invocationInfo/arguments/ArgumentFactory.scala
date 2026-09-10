package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.utils.SyntheticExpressionFactory.{wrapInSplatListExpression, wrapInTupleExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails.{ArgumentClauseTarget, ValueClause}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScArgumentExprList, ScAssignment, ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

object ArgumentFactory {

  val ArgumentCountLimit = 10

  def buildArguments(clause: ValueClause): List[Argument] = {
    implicit val context: ProjectContext = clause.argsElement.getProject
    val expressions = clause.argsElement match {
      case args: ScArgumentExprList => args.exprs
      case argument => argument.getContext.asOptionOf[MethodInvocation].toSeq.flatMap(_.argumentExpressions)
    }
    if (clause.isAutoTupling) {
      // Adapting f() to f(()) has no expressions to carry the parameter mapping.
      val parameter = clause.arguments.headOption.map(_._2).orElse {
        clause.target match {
          case ArgumentClauseTarget.ScalaParameters(parameters) => parameters.getSmartParameters.headOption
          case ArgumentClauseTarget.JavaParameters(parameters) => parameters.getParameters.headOption.map(Parameter(_))
          case ArgumentClauseTarget.Synthetic(function) => function.paramClauses.headOption.flatMap(_.headOption)
        }
      }
      parameter.toList.map { parameter =>
        Argument.fromArgParamMapping(wrapInTupleExpression(expressions) -> parameter)
      }
    } else buildArgumentsInEvaluationOrder(fixUnmatchedArguments(expressions, clause.arguments))
  }

  def buildUnmatchedArguments(expressions: Seq[ScExpression])(implicit context: ProjectContext): List[Argument] =
    buildArgumentsInEvaluationOrder(buildFakeParameters(expressions, initialIndex = 0))

  def insertThisArgToArgList(invocation: ScExpression, properArgs: List[Argument],
                             thisArgument: Argument): List[Argument] = invocation match {
    case infixExpression: ScInfixExpr if infixExpression.isRightAssoc && properArgs.nonEmpty =>
      properArgs.head :: thisArgument :: properArgs.tail
    case _ => thisArgument :: properArgs
  }

  private def fixUnmatchedArguments(args: Seq[ScExpression], matchedArgs: Seq[(ScExpression, Parameter)])
                                   (implicit context: ProjectContext): Seq[(ScExpression, Parameter)] = {
    // There might be more arguments than the method requires. In this case, we should still evaluate all of the arguments.
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

  private def buildArgumentsInEvaluationOrder(matchedParameters: Seq[(ScExpression, Parameter)])
                                             (implicit context: ProjectContext): List[Argument] = {
    val (matchedParams, maybeVarargArgument) = partitionNormalAndVarargArgs(matchedParameters)
    matchedParams
      .sortBy(ArgumentSorting.argumentPositionSortingKey)
      .map(Argument.fromArgParamMapping)
      .toList :++ maybeVarargArgument
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

  private def buildSplatListArgument(varargContents: Seq[ScExpression], varargParam: Parameter)
                                    (implicit context: ProjectContext): Argument = {
    val splatListArgument = wrapInSplatListExpression(varargContents)
    Argument.fromArgParamMapping((splatListArgument, varargParam))
  }
}
