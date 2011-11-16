package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Modifier, Evaluator}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.sun.jdi._


/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.11
 */

class ScalaEqEvaluator(left: Evaluator, right: Evaluator) extends Evaluator {
  def evaluate(context: EvaluationContextImpl): AnyRef = {
    val leftResult = left.evaluate(context).asInstanceOf[Value]
    val rightResult = right.evaluate(context).asInstanceOf[Value]
    val vm = context.getDebugProcess.getVirtualMachineProxy

    if (leftResult == null && rightResult == null) {
      return DebuggerUtilsEx.createValue(vm, "boolean", true)
    }
    if (leftResult == null) {
      return DebuggerUtilsEx.createValue(vm, "boolean", rightResult == leftResult)
    }
    if (rightResult == null) {
      return DebuggerUtilsEx.createValue(vm, "boolean", leftResult == rightResult)
    }
    if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
      val v1: Long = (leftResult.asInstanceOf[PrimitiveValue]).longValue
      val v2: Long = (rightResult.asInstanceOf[PrimitiveValue]).longValue
      return DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
    }
    if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
      val v1: Double = (leftResult.asInstanceOf[PrimitiveValue]).doubleValue
      val v2: Double = (rightResult.asInstanceOf[PrimitiveValue]).doubleValue
      return DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
    }
    if (leftResult.isInstanceOf[BooleanValue] && rightResult.isInstanceOf[BooleanValue]) {
      val v1: Boolean = (leftResult.asInstanceOf[PrimitiveValue]).booleanValue
      val v2: Boolean = (rightResult.asInstanceOf[PrimitiveValue]).booleanValue
      return DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
    }
    if (leftResult.isInstanceOf[CharValue] && rightResult.isInstanceOf[CharValue]) {
      val v1: Char = (leftResult.asInstanceOf[CharValue]).charValue
      val v2: Char = (rightResult.asInstanceOf[CharValue]).charValue
      return DebuggerUtilsEx.createValue(vm, "boolean", v1 == v2)
    }
    if (leftResult.isInstanceOf[ObjectReference] && rightResult.isInstanceOf[ObjectReference]) {
      val v1: ObjectReference = leftResult.asInstanceOf[ObjectReference]
      val v2: ObjectReference = rightResult.asInstanceOf[ObjectReference]
      return DebuggerUtilsEx.createValue(vm, "boolean", v1.uniqueID == v2.uniqueID)
    }
    throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "=="))
  }

  def getModifier: Modifier = null
}