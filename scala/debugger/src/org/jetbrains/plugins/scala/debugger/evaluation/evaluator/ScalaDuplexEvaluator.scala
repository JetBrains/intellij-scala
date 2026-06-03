package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ModifiableEvaluator, ModifiableValue}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}

/**
 * Tries to use first evaluator first. If gets exception or null, uses second one.
 */
case class ScalaDuplexEvaluator(first: Evaluator, second: Evaluator) extends ModifiableEvaluator {

  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = {
    try first.evaluateModifiable(context)
    catch {
      case e1: Exception if first != second =>
        try second.evaluateModifiable(context)
        catch {
          case e2: Exception =>
            val message = s"${e1.getMessage};${System.lineSeparator()} ${e2.getMessage}"
            throw EvaluateExceptionUtil.createEvaluateException(message)
        }
      case e: Exception => throw EvaluateExceptionUtil.createEvaluateException(e)
    }
  }
}
