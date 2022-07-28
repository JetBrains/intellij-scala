package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

class ScalaEqEvaluator(left: Evaluator, right: Evaluator) extends Evaluator {

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val leftResult = left.evaluate(context).asInstanceOf[Value]
    val rightResult = right.evaluate(context).asInstanceOf[Value]
    val vm = context.getDebugProcess.getVirtualMachineProxy
    (leftResult, rightResult) match {
      case (null, null) => DebuggerUtilsEx.createValue(vm, "boolean", true)
      case (null, _) => DebuggerUtilsEx.createValue(vm, "boolean", rightResult == leftResult)
      case (_, null) => DebuggerUtilsEx.createValue(vm, "boolean", leftResult == rightResult)
      case (v1: PrimitiveValue, v2: PrimitiveValue)
        if DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult) =>
        DebuggerUtilsEx.createValue(vm, "boolean", v1.longValue == v2.longValue)
      case (v1: PrimitiveValue, v2: PrimitiveValue)
        if DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult) =>
        DebuggerUtilsEx.createValue(vm, "boolean", v1.doubleValue == v2.doubleValue)
      case (v1: BooleanValue, v2: BooleanValue) => DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
      case (v1: CharValue, v2: CharValue) => DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
      case (v1: ObjectReference, v2: ObjectReference) => DebuggerUtilsEx.createValue(vm, "boolean", v1.uniqueID == v2.uniqueID)
      case _ =>
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.incompatible.types", "=="))
    }
  }
}