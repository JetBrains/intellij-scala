package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

private[evaluation] class LocalVarEvaluator(name: String) extends StackFrameVariableEvaluator {
  override protected val variableName: String = name
  override protected val isModifiable: Boolean = true
}
