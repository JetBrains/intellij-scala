package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}

/**
 * Nikolay.Tropin
 * 7/31/13
 */

/**
 * Tries to use first evaluator first. If gets exception or null, uses second one.
 */
class ScalaDuplexEvaluator(val first: Evaluator, val second: Evaluator) extends Evaluator {
  private var myModifier: Modifier = null

  def evaluate(context: EvaluationContextImpl): AnyRef = {
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
          case e2: Exception => throw EvaluateExceptionUtil.createEvaluateException(e1.getMessage + "\n " + e2.getMessage)
        }
      case e: Exception => throw EvaluateExceptionUtil.createEvaluateException(e)
    }
    result
  }

  def getModifier: Modifier = myModifier
}

object ScalaDuplexEvaluator {
  def unapply(evaluator: ScalaDuplexEvaluator): Option[(Evaluator, Evaluator)] = Option(evaluator.first, evaluator.second)
}
