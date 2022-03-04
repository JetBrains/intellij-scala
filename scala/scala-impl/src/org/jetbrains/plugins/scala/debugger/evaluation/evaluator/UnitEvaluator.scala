package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi.VoidValue

object UnitEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): VoidValue =
    context.getDebugProcess.getVirtualMachineProxy.mirrorOfVoid()
}
