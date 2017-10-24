package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaLiteralEvaluator(value: AnyRef, tp: ScType) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    if (value == null) return null
    val vm = context.getDebugProcess.getVirtualMachineProxy
    value match {
      case s: String => vm.mirrorOf(s)
      case b: java.lang.Boolean => DebuggerUtil.createValue(vm, tp, b.booleanValue())
      case c: java.lang.Character => DebuggerUtil.createValue(vm, tp, c.charValue())
      case f: java.lang.Float => DebuggerUtil.createValue(vm, tp, f.floatValue())
      case d: java.lang.Double => DebuggerUtil.createValue(vm, tp, d.doubleValue())
      case n: java.lang.Number => DebuggerUtil.createValue(vm, tp, n.longValue())
      case _ => throw EvaluationException("unknown type of literal")
    }
  }
}

object ScalaLiteralEvaluator {
  def apply(l: ScLiteral): ScalaLiteralEvaluator = {
    val tp = l.`type`().getOrAny
    val value = l.getValue
    if (value == null && !tp.isNull) {
      throw EvaluationException(s"Literal ${l.getText} has null value")
    }
    new ScalaLiteralEvaluator(value, tp)
  }
}