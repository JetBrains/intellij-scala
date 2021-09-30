package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.ArgumentFactory.buildArgumentsInEvaluationOrder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.{ArgParamMapping, Argument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScReferenceExpression}

case class InvocationInfo(invokedElement: Option[PsiElement], argsInEvaluationOrder: Seq[Argument],
                          argsMappedToParams: Seq[ArgParamMapping])

object InvocationInfo {

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    val target = invocation.target
    val isTupled = target.exists(_.tuplingUsed)

    val thisArgument = Argument.fromExpression(invocation.thisExpr, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(invocation, isTupled)

    InvocationInfo(target.map(_.element), thisArgument +: properArguments, Nil) // TODO generate and return mapping instead of Nil here
  }

  def fromReferenceExpression(referenceExpression: ScReferenceExpression): InvocationInfo = {
    val target = referenceExpression.bind().map(_.element)
    val thisArgument = Argument.fromExpression(referenceExpression.qualifier, ThisArgument, PassByValue)

    InvocationInfo(target, List(thisArgument), Nil)
  }
}
