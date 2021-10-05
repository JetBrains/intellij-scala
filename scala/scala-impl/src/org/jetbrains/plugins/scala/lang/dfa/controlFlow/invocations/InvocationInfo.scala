package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationChainExtractor.{innerInvocationChain, splitInvocationChain}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByValue, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ArgumentFactory.{buildArgumentsInEvaluationOrder, insertThisArgToArgList}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ParamToArgMapping.generateParamToArgMapping
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScReferenceExpression}


case class InvocationInfo(invokedElement: Option[InvokedElement],
                          argListsInEvaluationOrder: List[List[Argument]]) {

  val thisArgument: Option[Argument] = argListsInEvaluationOrder.headOption.flatMap(_.find(_.kind == ThisArgument))

  val properArguments: List[List[Argument]] = argListsInEvaluationOrder.map(_.filter(_.kind.is[ProperArgument]))

  /**
   * If paramToArgMapping(paramIndex) = argIndex, then the returned mapping maps
   * the parameter on position paramIndex in order in the function's parameter sequence
   * to the argument on position argIndex in the evaluation order of arguments in an invocation of this function.
   *
   * If the function has multiple parameter/argument lists, the lists are flattened and the indices are counted
   * disregarding the boundaries between the lists.
   *
   * @return list representing parameter-to-argument mapping for this invocation
   */
  val paramToArgMapping: List[Option[Int]] = generateParamToArgMapping(invokedElement, properArguments)
}

object InvocationInfo {

  def fromMethodCall(methodCall: ScMethodCall): Seq[InvocationInfo] = {
    val innerChain = innerInvocationChain(methodCall)
    splitInvocationChain(innerChain)
  }

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    val target = invocation.target
    val isTupled = target.exists(_.tuplingUsed)

    val thisArgument = Argument.fromExpression(invocation.thisExpr, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(invocation.matchedParameters, invocation, isTupled)
    val allArguments = insertThisArgToArgList(invocation, properArguments, thisArgument)

    InvocationInfo(InvokedElement.fromTarget(target, invocation.applicationProblems), List(allArguments))
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo = {
    val target = referenceExpression.bind()

    val thisArgument = Argument.fromExpression(referenceExpression.qualifier, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(referenceExpression.matchedParameters,
      referenceExpression, isTupled = false)

    InvocationInfo(InvokedElement.fromTarget(target, Nil), List(thisArgument :: properArguments))
  }
}
