package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi.Value

/**
 * Base trait for Scala language feature evaluators. Redefines the platform trait [[Evaluator]] method to return
 * a more specific type [[Value]].
 */
trait ValueEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value
}
