package org.jetbrains.plugins.scala.lang.dfa.framework

import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.{ScalaPsiElementTransformer, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{DfaConstantValue, constantValueToProblemMessage}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.jdk.CollectionConverters._

class ScalaDfaVisitor(private val problemsHolder: ProblemsHolder) extends ScalaElementVisitor {

  private val Log = Logger.getInstance(classOf[ScalaDfaVisitor])

  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
    val factory = new DfaValueFactory(problemsHolder.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))

    try {
      function.body.foreach(executeDataFlowAnalysis(_, problemsHolder, factory, memoryStates))
    } catch {
      case exception: TransformationFailedException =>
        Log.info(s"Dataflow analysis failed for function definition $function. Reason: $exception")
    }
  }

  private def executeDataFlowAnalysis(body: ScBlockStatement, problemsHolder: ProblemsHolder, factory: DfaValueFactory,
                                      memoryStates: Iterable[JvmDfaMemoryStateImpl]): Unit = {
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(factory, body)
    new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)
    val flow = controlFlowBuilder.build()

    val listener = new ScalaDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)
    if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
      reportProblems(listener, problemsHolder)
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }

  private def reportProblems(listener: ScalaDfaListener, problemsHolder: ProblemsHolder): Unit = {
    listener.collectConstantConditions
      .filter { case (_, value) => value != DfaConstantValue.Unknown }
      .foreach { case (anchor, value) => reportProblem(anchor, value, problemsHolder) }
  }

  private def reportProblem(anchor: ScalaDfaAnchor, value: DfaConstantValue, problemsHolder: ProblemsHolder): Unit = {
    anchor match {
      case statementAnchor: ScalaStatementAnchor =>
        val statement = statementAnchor.statement
        val message = constantValueToProblemMessage(value, getProblemTypeForStatement(statementAnchor.statement))
        if (!shouldSuppress(statement, value)) {
          problemsHolder.registerProblem(statement, message)
        }
      case _ =>
    }
  }

  private def getProblemTypeForStatement(statement: ScBlockStatement): ProblemHighlightType = statement match {
    case _: ScLiteral => ProblemHighlightType.WEAK_WARNING
    case _ => ProblemHighlightType.GENERIC_ERROR_OR_WARNING
  }

  private def shouldSuppress(statement: ScBlockStatement, value: DfaConstantValue): Boolean = {
    // TODO implement
    false
  }
}
