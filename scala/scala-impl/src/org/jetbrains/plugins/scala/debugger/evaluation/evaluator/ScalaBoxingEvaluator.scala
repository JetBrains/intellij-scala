package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{BoxingEvaluator, Evaluator, IdentityEvaluator, Modifier}
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

case class ScalaBoxingEvaluator(evaluator: Evaluator) extends Evaluator {
  
  override def evaluate(context: EvaluationContextImpl): AnyRef = ScalaBoxingEvaluator.box(evaluator.evaluate(context), context)

  override def getModifier: Modifier = null
}

object ScalaBoxingEvaluator {
  def box(x: AnyRef, context: EvaluationContextImpl): AnyRef = {
    x match {
      case null => null
      case DebuggerUtil.scalaRuntimeRefTo(value: Value) =>
        new BoxingEvaluator(new IdentityEvaluator(value)).evaluate(context)
      case v: Value =>
        new BoxingEvaluator(new IdentityEvaluator(v)).evaluate(context)
      case result =>
        throw EvaluationException(ScalaBundle.message("cannot.perform.boxing.conversion.for.result", result))
    }
  }
}
