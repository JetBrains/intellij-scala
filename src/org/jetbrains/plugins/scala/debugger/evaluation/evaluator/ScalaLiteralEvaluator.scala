package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Modifier, Evaluator}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaLiteralEvaluator(value: AnyRef, tp: ScType) extends Evaluator {
  def evaluate(context: EvaluationContextImpl): AnyRef = {
    if (value == null) return null
    val vm = context.getDebugProcess.getVirtualMachineProxy
    value match {
      case s: String => vm.mirrorOf(s)
      case b: java.lang.Boolean => DebuggerUtil.createValue(vm, tp, b.booleanValue())
      case c: java.lang.Character => DebuggerUtil.createValue(vm, tp, c.charValue())
      case f: java.lang.Float => DebuggerUtil.createValue(vm, tp, f.floatValue())
      case d: java.lang.Double => DebuggerUtil.createValue(vm, tp, d.doubleValue())
      case n: java.lang.Number => DebuggerUtil.createValue(vm, tp, n.longValue())
      case _ => throw EvaluateExceptionUtil.createEvaluateException("unknown type of literal")
    }
  }

  def getModifier: Modifier = null
}