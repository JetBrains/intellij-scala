package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.constantValueToProblemMessage
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

  private def executeDataFlowAnalysis(body: ScExpression, problemsHolder: ProblemsHolder, factory: DfaValueFactory,
                                      memoryStates: Iterable[JvmDfaMemoryStateImpl]): Unit = {
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(body, factory)
    for (flow <- controlFlowBuilder.buildFlow()) {
      val listener = new ScalaDfaListener
      val interpreter = new StandardDataFlowInterpreter(flow, listener)
      if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
        reportProblems(listener, problemsHolder)
      }
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }

  private def reportProblems(listener: ScalaDfaListener, problemsHolder: ProblemsHolder): Unit = {
    listener.constantConditions
      .filter { case (_, value) => value != DfaConstantValue.Unknown }
      // TODO suppressing unwanted warnings
      .foreach { case (anchor, value) => reportProblem(anchor, constantValueToProblemMessage(value), problemsHolder) }
  }

  private def reportProblem(anchor: ScalaDfaAnchor, @Nls message: String, problemsHolder: ProblemsHolder): Unit = {
    anchor match {
      case expressionAnchor: ScalaExpressionAnchor => problemsHolder.registerProblem(expressionAnchor.expression, message)
    }
  }
}
