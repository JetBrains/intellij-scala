package org.jetbrains.plugins.scala.debugger.evaluation.newevaluator

private[evaluation] class VarEvaluator(name: String) extends LocalVariableEvaluator {
  override protected val variableName: String = name
  override protected val isModifiable: Boolean = true
}
