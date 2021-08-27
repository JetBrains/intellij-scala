package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class DfaConstantConditionsVisitor(problemsHolder: ProblemsHolder) extends ScalaRecursiveElementVisitor {

  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
    val factory = new DfaValueFactory(problemsHolder.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))
    function.body.foreach(executeDataFlowAnalysis(_, problemsHolder, factory, memoryStates))
  }

  def executeDataFlowAnalysis(body: ScExpression, problemsHolder: ProblemsHolder, factory: DfaValueFactory,
                              memoryStates: Iterable[JvmDfaMemoryStateImpl]): Unit = {
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(body, factory)
    for (flow <- controlFlowBuilder.buildFlow()) {
      println(flow)
    }
  }
}
