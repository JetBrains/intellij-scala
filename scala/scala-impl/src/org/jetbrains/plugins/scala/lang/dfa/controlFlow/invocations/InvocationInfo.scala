package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

// TODO Map[Argument, ScParameter] generated at some point
case class InvocationInfo(invokedElement: Option[PsiElement], argsInEvaluationOrder: Seq[Argument])

object InvocationInfo {

  def fromMethodInvocation(invocation: MethodInvocation): InvocationInfo = {
    val target = invocation.target
    val isTupled = target.exists(_.tuplingUsed)

    val thisArgument = Argument.fromExpression(invocation.thisExpr, ThisArgument, PassByValue)
    val properArguments = buildArgumentsInEvaluationOrder(invocation.matchedParameters, isTupled)

    InvocationInfo(target.map(_.element), thisArgument +: properArguments)
  }

  private def buildArgumentsInEvaluationOrder(matchedParameters: Seq[(ScExpression, Parameter)],
                                              isTupled: Boolean): Seq[Argument] = Nil // TODO implement
}
