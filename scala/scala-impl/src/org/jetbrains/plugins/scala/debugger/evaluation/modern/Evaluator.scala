package org.jetbrains.plugins.scala.debugger.evaluation.modern

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator => PlatformEvaluator, _}
import com.sun.jdi.Value

private trait Evaluator extends PlatformEvaluator {
  override def evaluate(context: EvaluationContextImpl): Value
}
