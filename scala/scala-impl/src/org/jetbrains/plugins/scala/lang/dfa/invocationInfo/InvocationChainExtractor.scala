package org.jetbrains.plugins.scala.lang.dfa.invocationInfo

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.{buildAllArguments, insertThisArgToArgList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec

object InvocationChainExtractor {

  def innerInvocationChain(methodCall: ScMethodCall): List[MethodInvocation] = {
    @tailrec
    def inner(currentExpression: ScExpression,
              collectedInvocations: List[MethodInvocation]): List[MethodInvocation] =
      currentExpression match {
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
      case Some(ScalaResolveResult(_: PsiMethod, _)) => (Nil, rest)
      case _ => rest.span(_._2.isEmpty)
    }

    val allMatchedArgs = call.matchedParameters +: restArgs.map(_._1.matchedParameters)
    val allArgExpressions = call.argumentExpressions +: restArgs.map(_._1.argumentExpressions)

    val sortedMatchedParameters = buildAllArguments(allMatchedArgs, allArgExpressions, call, isTupled)
    val thisArgument = Argument.fromExpression(call.thisExpr, ThisArgument, PassByValue)
    val argumentsListsWithThis = insertThisArgToArgList(call, sortedMatchedParameters.head, thisArgument) +:
      sortedMatchedParameters.tail

    val invokedElement = InvokedElement.fromTarget(target, call.applicationProblems)
    val invocationInfo = InvocationInfo(invokedElement, argumentsListsWithThis, call)
    invocationInfo :: collectInvocationsInfo(followingCalls)
  }
}
