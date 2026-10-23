package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ModifiableEvaluator, ModifiableValue}
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence

trait ScSuperEvaluator

class ScalaSuperEvaluator(iterations: Int = 0) extends ScalaThisEvaluator(iterations) with ScSuperEvaluator

class ScalaSuperDelegate(delegate: Evaluator) extends ModifiableEvaluator with ScSuperEvaluator {
  //noinspection ApiStatus,UnstableApiUsage
  @RequiresReadLockAbsence
  override def evaluateModifiable(context: EvaluationContextImpl): ModifiableValue = delegate.evaluateModifiable(context)
}
