package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.jdk.CollectionConverters._

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
      val listener = new ScalaDfaListener
      val interpreter = new StandardDataFlowInterpreter(flow, listener)
      if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
        println(listener.constantConditions)
        // TODO report problems to the user
      }
    }
  }

  def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                             flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }
}
