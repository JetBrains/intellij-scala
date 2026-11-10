package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence

/**
 * Like `{ val v = value; body(v) }`: every evaluation of this evaluator evaluates `value` exactly once, and the
 * evaluator built by `body` reads the result through the evaluator it is given, as many times as it needs. This keeps
 * an expression with side effects (the right-hand side of a pattern definition, a call to `unapply`) from running
 * again for each place that uses its result.
 *
 * The result does not outlive the evaluation it belongs to. A built evaluator is evaluated many times: the platform
 * keeps the evaluator of a breakpoint condition for every hit of the breakpoint, and an interpreted loop evaluates its
 * body on every iteration, so a result kept any longer would be stale.
 */
private[evaluation] final class ScalaSharedResultEvaluator(value: Evaluator)(body: Evaluator => Evaluator) extends Evaluator:
  // Evaluators run on the debugger manager thread, one evaluation at a time. None means no evaluation is in progress;
  // Some(null) is a valid result, since JDI represents a null reference as null.
  private var current: Option[AnyRef] = None

  private object CurrentValue extends Evaluator:
    override def evaluate(context: EvaluationContextImpl): AnyRef =
      current.getOrElse(throw IllegalStateException("The value is read outside of the evaluation that computes it"))

  private val bodyEvaluator: Evaluator = body(CurrentValue)

  //noinspection ApiStatus,UnstableApiUsage
  @RequiresReadLockAbsence
  override def evaluate(context: EvaluationContextImpl): AnyRef =
    val outer = current
    current = Some(value.evaluate(context))
    try bodyEvaluator.evaluate(context)
    finally current = outer
