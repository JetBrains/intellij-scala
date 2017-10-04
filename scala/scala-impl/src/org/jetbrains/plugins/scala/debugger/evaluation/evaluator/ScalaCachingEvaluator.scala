package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}

/**
 * @author Nikolay.Tropin
 */
class ScalaCachingEvaluator(evaluator: Evaluator) extends Evaluator {
  private var result: Option[AnyRef] = None

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    result.getOrElse {
      val res = evaluator.evaluate(context)
      result = Some(res)
      res
    }
  }

  override def getModifier: Modifier = null
}
