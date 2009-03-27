package org.jetbrains.plugins.scala.debugger.evaluation.evaluators

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.03.2009
 */

class EmptyEvaluator extends Evaluator {
  def evaluate(context: EvaluationContextImpl): AnyRef = {
    val vm = context.getDebugProcess.getVirtualMachineProxy
    vm.mirrorOf
  }

  def getModifier: Modifier = null
}