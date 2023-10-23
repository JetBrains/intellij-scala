package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework._
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import java.util.EmptyStackException
import scala.jdk.CollectionConverters._

class ScalaDfaVisitor(private val problemsHolder: ProblemsHolder, buildUnsupportedPsiElements: Boolean = true) extends ScalaElementVisitor {

  private val Log = Logger.getInstance(classOf[ScalaDfaVisitor])

  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit =
    function.body.foreach(executeDataFlowAnalysis(_, function))

  private def errorMessage(functionName: String, reason: String): String = {
    s"Dataflow analysis failed for function definition $functionName. Reason: $reason"
  }

  private def executeDataFlowAnalysis(body: ScBlockStatement, function: ScFunctionDefinition): Unit = {
    val factory = new DfaValueFactory(problemsHolder.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))

    val analysedMethodInfo = AnalysedMethodInfo(function, 1)
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, factory, body, buildUnsupportedPsiElements)
    controlFlowBuilder.transformStatement(body, ResultReq.None)
    val flow = controlFlowBuilder.build()
    val listener = new ScalaDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)
    if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
      val problemReporter = new ScalaDfaProblemReporter(problemsHolder)
      problemReporter.reportProblems(listener)
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }
}
