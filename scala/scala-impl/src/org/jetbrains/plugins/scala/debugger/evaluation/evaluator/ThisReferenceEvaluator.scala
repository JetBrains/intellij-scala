package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNewTemplateDefinition, ScThisReference}

private[evaluation] object ThisReferenceEvaluator {
  def create(ref: ScThisReference): ValueEvaluator =
    ref.refTemplate.collect {
      case ntd: ScNewTemplateDefinition => ntd.supers.head
      case td => td
    }.map(ExpressionEvaluatorBuilder.calculateDebuggerName)
      .fold(StackWalkingThisEvaluator.closest)(StackWalkingThisEvaluator.ofType)
}
