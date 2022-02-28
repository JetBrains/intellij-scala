package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.{EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.engine.evaluation.expression.{ExpressionEvaluator, Modifier}
import com.sun.jdi.Value

class ScalaCompilingExpressionEvaluator(evaluator: ScalaCompilingEvaluator) extends ExpressionEvaluator {
  override def evaluate(context: EvaluationContext): Value = evaluator.evaluate(context.asInstanceOf[EvaluationContextImpl])

  override def getModifier: Modifier = evaluator.getModifier
}
