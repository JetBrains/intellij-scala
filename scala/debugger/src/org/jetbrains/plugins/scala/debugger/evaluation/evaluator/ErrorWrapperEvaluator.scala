package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

/**
 * Evaluates the wrapped evaluator.
 *
 * If an error is obtained, an evaluation exception with the provided message is thrown instead.
 */
class ErrorWrapperEvaluator(evaluator: Evaluator, @Nls message: => String) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    try {
      evaluator.evaluate(context)
    } catch {
      case _: Exception =>
        throw EvaluationException(message)
    }
  }
}
