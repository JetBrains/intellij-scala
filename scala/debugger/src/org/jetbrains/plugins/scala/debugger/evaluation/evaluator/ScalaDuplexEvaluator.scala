package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

/**
 * Tries to use first evaluator first. If gets exception or null, uses second one.
 */
case class ScalaDuplexEvaluator(first: Evaluator, second: Evaluator) extends Evaluator {

  private var myModifier: Modifier = _

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    var result: AnyRef = null
    try {
      result = first.evaluate(context)
      myModifier = first.getModifier
    }
    catch {
      case e1: Exception if first != second =>
        try {
          result = second.evaluate(context)
          myModifier = second.getModifier
        }
        catch {
          case e2: Exception =>
            val message = s"${e1.getMessage}${System.lineSeparator()} ${e2.getMessage}"
            throw EvaluationException(message)
        }
      case e: Exception => throw EvaluationException(e)
    }
    result
  }

  override def getModifier: Modifier = myModifier
}
