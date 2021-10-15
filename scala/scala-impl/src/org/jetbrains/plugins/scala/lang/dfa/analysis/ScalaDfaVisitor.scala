package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState}
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.{ScalaPsiElementTransformer, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.DfaConstantValue
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{constantValueToProblemMessage, exceptionNameToProblemMessage}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScInfixExpr, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import java.util.EmptyStackException
import scala.jdk.CollectionConverters._

class ScalaDfaVisitor(private val problemsHolder: ProblemsHolder) extends ScalaElementVisitor {

  private val Log = Logger.getInstance(classOf[ScalaDfaVisitor])

  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
    try {
      function.body.foreach(executeDataFlowAnalysis)
    } catch {
      case transformationFailed: TransformationFailedException =>
        Log.info(errorMessage(function.name, transformationFailed.toString))
      case _: EmptyStackException => Log.info(errorMessage(function.name, "empty stack"))
      case _: Exception => Log.info(errorMessage(function.name, "other"))
    }
  }

  private def errorMessage(functionName: String, reason: String): String = {
    s"Dataflow analysis failed for function definition $functionName. Reason: $reason"
  }

  private def executeDataFlowAnalysis(body: ScBlockStatement): Unit = {
    val factory = new DfaValueFactory(problemsHolder.getProject)
    val memoryStates = List(new JvmDfaMemoryStateImpl(factory))

    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(factory, body)
    new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)
    val flow = controlFlowBuilder.build()

    val listener = new ScalaDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)
    if (interpreter.interpret(buildInterpreterStates(memoryStates, flow).asJava) == RunnerResult.OK) {
      reportProblems(listener)
    }
  }

  private def buildInterpreterStates(memoryStates: Iterable[JvmDfaMemoryStateImpl],
                                     flow: ControlFlow): List[DfaInstructionState] = {
    memoryStates.map(new DfaInstructionState(flow.getInstruction(0), _)).toList
  }

  private def reportProblems(listener: ScalaDfaListener): Unit = {
    listener.collectConstantConditions
      .filter { case (_, value) => value != DfaConstantValue.Unknown }
      .foreach { case (anchor, value) => reportConstantCondition(anchor, value) }

    listener.collectUnsatisfiedConditions
      .filter { case (_, occurred) => occurred == ThreeState.YES }
      .foreach { case (problem, _) => reportUnsatisfiedProblem(problem) }
  }

  private def reportConstantCondition(anchor: ScalaDfaAnchor, value: DfaConstantValue): Unit = {
    anchor match {
      case statementAnchor: ScalaStatementAnchor =>
        val statement = statementAnchor.statement
        val message = constantValueToProblemMessage(value, getProblemTypeForStatement(statementAnchor.statement))
        if (!shouldSuppress(statement)) {
          problemsHolder.registerProblem(statement, message)
        }
      case _ =>
    }
  }

  private def reportUnsatisfiedProblem(problem: ScalaDfaProblem): Unit = {
    problem match {
      case ScalaCollectionAccessProblem(_, accessExpression, exceptionName) =>
        val message = exceptionNameToProblemMessage(exceptionName)
        problemsHolder.registerProblem(accessExpression, message)
      case ScalaNullAccessProblem(accessExpression) =>
        val message = exceptionNameToProblemMessage(NullPointerExceptionName)
        problemsHolder.registerProblem(accessExpression, message)
      case _ =>
    }
  }

  private def getProblemTypeForStatement(statement: ScBlockStatement): ProblemHighlightType = statement match {
    case _: ScLiteral => ProblemHighlightType.WEAK_WARNING
    case _ => ProblemHighlightType.GENERIC_ERROR_OR_WARNING
  }

  private def shouldSuppress(statement: ScBlockStatement): Boolean = {
    val parent = findProperParent(statement)
    statement match {
      // TODO more complete implementation
      case _: ScLiteral => true
      case infix: ScInfixExpr => parent match {
        case Some(parentInfix: ScInfixExpr) => parentInfix.operation.refName == infix.operation.refName
        case _ => false
      }
      case _ => false
    }
  }

  private def findProperParent(statement: ScBlockStatement): Option[PsiElement] = {
    var parent = statement.parent
    while (parent match {
      case Some(_: ScParenthesisedExpr) => true
      case _ => false
    }) {
      parent = parent.get.asInstanceOf[ScParenthesisedExpr].innerElement
    }
    parent
  }
}
