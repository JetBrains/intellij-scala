package org.jetbrains.plugins.scala.lang.dfa.cfg.transformations

import org.jetbrains.plugins.scala.lang.dfa.cfg.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.cfg.invocations.InvocationInfo
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
  def transformSpecially(invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Unit = ???
}
