package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}

import scala.util.Try

case class FromLocalArgEvaluator(delegate: Evaluator) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): AnyRef =
    evaluateNotFromField(delegate, context)

  override def getModifier: Modifier = null

  //it is hard to distinguish fields from local vars in async block
  private def evaluateNotFromField(evaluator: Evaluator, context: EvaluationContextImpl): Option[AnyRef] = {
    evaluator match {
      case ScalaBoxingEvaluator(inner) => evaluateNotFromField(inner, context).map(ScalaBoxingEvaluator.box(_, context))
      case _: ScalaFieldEvaluator => None
      case ScalaDuplexEvaluator(_: ScalaFieldEvaluator, _: ScalaFieldEvaluator) => None
      case ScalaDuplexEvaluator(first: ScalaFieldEvaluator, second) if Try(first.evaluate(context)).isFailure =>
        Some(second.evaluate(context))
      case ScalaDuplexEvaluator(first, _: ScalaFieldEvaluator) => Try(first.evaluate(context)).toOption
      case _ => Some(evaluator.evaluate(context))
    }
  }
}