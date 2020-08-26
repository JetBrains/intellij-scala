package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}

/**
 * Nikolay.Tropin
 * 7/24/13
 */
class ScalaBlockExpressionEvaluator(statements: collection.Seq[Evaluator]) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val void: AnyRef = context.getSuspendContext.getDebugProcess.getVirtualMachineProxy.mirrorOfVoid()
    statements.foldLeft(void)((_, stmt) => stmt.evaluate(context))
  }
}
