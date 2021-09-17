package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation

class InvocationTransformer(invocation: MethodInvocation) extends ScalaPsiElementTransformer(invocation) {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    val invocationInfo = InvocationInfo.fromMethodInvocation(invocation)

    if (hasSpecialSupport(invocationInfo)) {
      transformSpecially(invocationInfo, builder)
    } else {
      builder.pushUnknownCall(invocation, invocationInfo.argsInEvaluationOrder.size)
    }
  }

  private def hasSpecialSupport(invocationInfo: InvocationInfo): Boolean = {
    false // TODO implement
  }

  // TODO implement
  private def transformSpecially(invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Unit = ???
}
