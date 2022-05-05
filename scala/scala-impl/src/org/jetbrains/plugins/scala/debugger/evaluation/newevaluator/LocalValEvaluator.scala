package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

private[evaluation] class LocalValEvaluator(name: String) extends StackFrameVariableEvaluator {
  override protected val variableName: String = name
  override protected val isModifiable: Boolean = false
}
