package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.expression.CodeFragmentEvaluator
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.{Type, Value, VirtualMachine}

import scala.collection.mutable

/**
 * @author Nikolay.Tropin
 */
class SyntheticVariablesHolderEvaluator(parentEvaluator: CodeFragmentEvaluator) extends CodeFragmentEvaluator(parentEvaluator) {
  private val mySyntheticLocals = mutable.HashMap[String, Value]()

  override def getValue(localName: String, vm: VirtualMachineProxyImpl): Value = mySyntheticLocals.get(localName) match {
    case None => parentEvaluator.getValue(localName, vm)
    case Some(v) => v
  }

  override def setInitialValue(localName: String, value: scala.Any): Unit = {
    if (mySyntheticLocals.contains(localName)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.variable.already.declared", localName))
    }
    mySyntheticLocals.put(localName, NonInitializedValue)
  }

  override def setValue(localName: String, value: Value): Unit = {
    if (mySyntheticLocals.contains(localName)) mySyntheticLocals.put(localName, value)
    else parentEvaluator.setValue(localName, value)
  }

}

private object NonInitializedValue extends Value {
  override def `type`(): Type = null
  override def virtualMachine(): VirtualMachine = null
}
