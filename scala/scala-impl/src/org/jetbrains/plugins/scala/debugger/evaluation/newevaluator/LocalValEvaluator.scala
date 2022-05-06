package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

private[evaluation] class LocalValEvaluator(
  override protected val variableName: String,
  override protected val sourceFileName: String
) extends StackFrameVariableEvaluator {
  override protected val isModifiable: Boolean = false
}
