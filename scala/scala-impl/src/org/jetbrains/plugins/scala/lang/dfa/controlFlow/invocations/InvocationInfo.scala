package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByValue, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ArgumentFactory.buildArgumentsInEvaluationOrder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.{ArgParamMapping, Argument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScInfixExpr, ScReferenceExpression}

case class InvocationInfo(invokedElement: Option[InvokedElement],
                          argsInEvaluationOrder: Seq[Argument],
                          argsMappedToParams: Seq[ArgParamMapping]) {

  def thisArgument: Option[Argument] = argsInEvaluationOrder.find(_.kind == ThisArgument)

  def properArguments: Seq[Argument] = argsInEvaluationOrder.filter(_.kind.is[ProperArgument])
}

object InvocationInfo {

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    val target = invocation.target
    val isTupled = target.exists(_.tuplingUsed)

    val thisArgument = Argument.fromExpression(invocation.thisExpr, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(invocation, isTupled).toList

    val arguments = invocation match {
      case infixExpression: ScInfixExpr if infixExpression.isRightAssoc =>
        properArguments.head :: thisArgument :: properArguments.tail
      case _ => thisArgument :: properArguments
    }

    InvocationInfo(target.map(_.element).map(InvokedElement), arguments, Nil) // TODO generate and return mapping instead of Nil here
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo = {
    val target = referenceExpression.bind().map(_.element)

    val thisArgument = Argument.fromExpression(referenceExpression.qualifier, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(referenceExpression, isTupled = false).toList

    InvocationInfo(target.map(InvokedElement), thisArgument :: properArguments, Nil) // TODO mapping instead of Nil also here
  }
}
