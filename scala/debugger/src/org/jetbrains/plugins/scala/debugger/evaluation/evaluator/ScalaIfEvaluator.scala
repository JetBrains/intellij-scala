package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, IfStatementEvaluator}

class ScalaIfEvaluator(condition: Evaluator, ifBranch: Evaluator, elseBranch: Option[Evaluator])
  extends IfStatementEvaluator(condition, ifBranch, elseBranch.orNull) {

  private val elseBranchIsDefined: Boolean = elseBranch.isDefined

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    val result = super.evaluate(context)
    if (elseBranchIsDefined) result else context.getDebugProcess.getVirtualMachineProxy.mirrorOfVoid()
  }
}
