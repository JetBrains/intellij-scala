package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ArgumentUtils.{ArgParamMapping, buildArgumentsInEvaluationOrder}
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation

case class InvocationInfo(invokedElement: Option[PsiElement], argsInEvaluationOrder: Seq[Argument],
                          argsMappedToParams: Seq[ArgParamMapping])

object InvocationInfo {

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    val target = invocation.target
    val isTupled = target.exists(_.tuplingUsed)

    val thisArgument = Argument.fromExpression(invocation.thisExpr, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(invocation.matchedParameters) // TODO do I need to pass isTupled already here?

    InvocationInfo(target.map(_.element), thisArgument +: properArguments, Nil) // TODO generate and return mapping instead of Nil here
  }
}
