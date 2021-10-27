package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.DfaConstantValue
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages.NullPointerExceptionName
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.SyntheticOperators.LogicalBinary
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{constantValueToProblemMessage, exceptionNameToProblemMessage}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaDfaProblemReporter(problemsHolder: ProblemsHolder) {

  def reportProblems(listener: ScalaDfaListener): Unit = {
    listener.collectConstantConditions
      .filter { case (_, value) => value != DfaConstantValue.Other }
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
        if (!shouldSuppress(statement, value)) {
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

  private def shouldSuppress(statement: ScBlockStatement, value: DfaConstantValue): Boolean = {
    val parent = findProperParent(statement)
    statement match {
      case _: ScLiteral => true
      // Warning will be reported for the prefix expression
      case _: ScExpression if parent.exists(_.is[ScPrefixExpr]) => true
      case invocation: MethodInvocation if invocation.applicationProblems.nonEmpty => true
      // Warning will be reported for the parent expression
      case infix: ScInfixExpr if LogicalBinary.contains(infix.operation.refName) => parent match {
        case Some(parentInfix: ScInfixExpr) => parentInfix.operation.refName == infix.operation.refName
        case _ => false
      }
      // Sometimes we pass constant values to methods just to name them, highlighting this could be annoying
      case _: ScReferenceExpression if parent.exists(_.is[ScArgumentExprList]) => true
      // Sometimes we create constant boolean values just to name them, highlighting this could be annoying
      case _: ScReferenceExpression if value == DfaConstantValue.True || value == DfaConstantValue.False => true
      case _ => false
    }
  }

  private def findProperParent(statement: ScBlockStatement): Option[PsiElement] = {
    var parent = statement.parent
    while (parent match {
      case Some(_: ScParenthesisedExpr) => true
      case _ => false
    }) {
      parent = parent.get.parent
    }
    parent
  }
}
