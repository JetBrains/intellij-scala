package org.jetbrains.plugins.scala.debugger.evaluation.modern

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.Value

private object UnitEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value =
    context.getDebugProcess.getVirtualMachineProxy.mirrorOfVoid()
}
