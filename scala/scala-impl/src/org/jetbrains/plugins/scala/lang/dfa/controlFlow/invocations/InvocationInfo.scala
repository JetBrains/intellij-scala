package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationChainExtractor.{innerInvocationChain, splitInvocationChain}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByValue, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ArgumentFactory.buildArgumentsInEvaluationOrder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScInfixExpr, ScMethodCall, ScReferenceExpression}

case class InvocationInfo(invokedElement: Option[InvokedElement],
                          argListsInEvaluationOrder: Seq[Seq[Argument]]) {

  def thisArgument: Option[Argument] = argListsInEvaluationOrder.headOption.flatMap(_.find(_.kind == ThisArgument))

  def properArguments: Seq[Seq[Argument]] = argListsInEvaluationOrder.map(_.filter(_.kind.is[ProperArgument]))

  // TODO generate param to arg mapping via a method
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
    val properArguments = buildArgumentsInEvaluationOrder(invocation.matchedParameters, isTupled).toList

    val arguments = invocation match {
      case infixExpression: ScInfixExpr if infixExpression.isRightAssoc =>
        properArguments.head :: thisArgument :: properArguments.tail
      case _ => thisArgument :: properArguments
    }

    InvocationInfo(target.map(_.element).map(InvokedElement), List(arguments))
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo = {
    val target = referenceExpression.bind().map(_.element)

    val thisArgument = Argument.fromExpression(referenceExpression.qualifier, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(referenceExpression.matchedParameters, isTupled = false).toList

    InvocationInfo(target.map(InvokedElement), List(thisArgument :: properArguments))
  }
}
