package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ArgumentFactory.{buildArgumentsInEvaluationOrder, insertThisArgToArgList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

object InvocationChainExtractor {

  def innerInvocationChain(methodCall: ScMethodCall): List[MethodInvocation] = {
    @tailrec
    def inner(currentExpression: ScExpression,
              collectedInvocations: List[MethodInvocation]): List[MethodInvocation] = currentExpression match {
      case methodCall: ScMethodCall => inner(methodCall.getEffectiveInvokedExpr, methodCall :: collectedInvocations)
      case infixCall: ScInfixExpr => infixCall :: collectedInvocations
      case _ => collectedInvocations
    }

    inner(methodCall.getEffectiveInvokedExpr, methodCall :: Nil)
  }

  def splitInvocationChain(chain: List[MethodInvocation]): List[InvocationInfo] = {
    collectInvocationsInfo(chain.map(call => call -> call.target))
  }

  private def collectInvocationsInfo(remainingCalls: List[(MethodInvocation, Option[ScalaResolveResult])]): List[InvocationInfo] = {
    if (remainingCalls.isEmpty) {
      return Nil
    }

    val (call, resolveResult) :: rest = remainingCalls
    val target = resolveResult.map(_.mostInnerResolveResult)
    val isTupled = target.exists(_.tuplingUsed)

    val (restArgs, followingCalls) = target match {
      case Some(ScalaResolveResult(scalaFunction: ScParameterOwner, _)) => rest.splitAt(scalaFunction.allClauses.length - 1)
      case Some(ScalaResolveResult(syntheticFunction: ScFun, _)) => rest.splitAt(syntheticFunction.paramClauses.length - 1)
      case Some(ScalaResolveResult(javaFunction: PsiMethod, _)) => (Nil, rest)
      case _ => rest.span(_._2.isEmpty)
    }

    val allMatchedArgs = call.matchedParameters +: restArgs.map(_._1.matchedParameters)
    val allArgExpressions = call.argumentExpressions +: restArgs.map(_._1.argumentExpressions)

    implicit val context: ProjectContext = call.getProject
    // There might be more arguments than the function requires. In this case, we should still evaluate the arguments.
    val fixedArgs = allArgExpressions.zip(allMatchedArgs).map {
      case (expressions, argParams) => fixUnmatchedArguments(expressions, argParams)
    }

    val sortedMatchedParameters = fixedArgs.map(buildArgumentsInEvaluationOrder(_, call, isTupled))
    val thisArgument = Argument.fromExpression(call.thisExpr, ThisArgument, PassByValue)
    val argumentsListsWithThis = insertThisArgToArgList(call, sortedMatchedParameters.head, thisArgument) +:
      sortedMatchedParameters.tail

    val invocationInfo = InvocationInfo(InvokedElement.fromTarget(target, call.applicationProblems), argumentsListsWithThis)
    invocationInfo :: collectInvocationsInfo(followingCalls)
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
}
