package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{BoxingEvaluator, Evaluator, IdentityEvaluator, Modifier}
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

/**
 * Nikolay.Tropin
 * 2014-10-24
 */
class ScalaBoxingEvaluator(evaluator: Evaluator) extends Evaluator {
  
  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    evaluator.evaluate(context) match {
      case DebuggerUtil.scalaRuntimeRefTo(value: Value) =>
        new BoxingEvaluator(new IdentityEvaluator(value)).evaluate(context)
      case _ =>
        new BoxingEvaluator(evaluator).evaluate(context)
    }
  }

  override def getModifier: Modifier = null
}
