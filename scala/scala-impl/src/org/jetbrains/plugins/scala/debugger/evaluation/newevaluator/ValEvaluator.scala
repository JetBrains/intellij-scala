package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

private[evaluation] class ValEvaluator(name: String) extends LocalVariableEvaluator {
  override protected val variableName: String = name
  override protected val isModifiable: Boolean = false
}
