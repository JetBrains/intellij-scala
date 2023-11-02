package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.DfaConstantValue
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators.LogicalBinary
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.constantValueToProblemMessage
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaDfaProblemReporter(problemsHolder: ProblemsHolder) {

  def reportProblems(result: ScalaDfaResult): Unit = {
    result.collectConstantConditions
      .filter { case (_, value) => value != DfaConstantValue.Other }
      .foreach { case (anchor, value) => reportConstantCondition(anchor, value) }

    result.collectUnsatisfiedConditions
      .filter { case (_, occurrence) => occurrence.shouldReport }
      .foreach { case (problem, occurrence) => problem.registerTo(problemsHolder, occurrence) }
  }

  private def reportConstantCondition(anchor: ScalaDfaAnchor, value: DfaConstantValue): Unit = {
    anchor match {
      case statementAnchor: ScalaDfaAnchorWithPsiElement =>
        val element = statementAnchor.psiElement
        val message = constantValueToProblemMessage(value, getProblemTypeForStatement(element))
        if (!shouldSuppress(element, value)) {
          problemsHolder.registerProblem(element, message)
        }
      case _ =>
    }
  }

  private def getProblemTypeForStatement(statement: PsiElement): ProblemHighlightType = statement match {
    case _: ScLiteral => ProblemHighlightType.WEAK_WARNING
    case _: ScReferenceExpression => ProblemHighlightType.WEAK_WARNING
    case _ => ProblemHighlightType.GENERIC_ERROR_OR_WARNING
  }

  private def shouldSuppress(element: PsiElement, value: DfaConstantValue): Boolean = {
    val parent = findProperParent(element)
    element match {
      case _: ScLiteral => true
      // Primary purpose of such larger blocks is usually to have side effects, not just return a value,
      // highlighting a large block in general can be very annoying
      case block: ScBlockExpr if block.statements.size > 3 => true
      // Warning will be reported for the prefix expression
      case _: ScExpression if parent.exists(_.is[ScPrefixExpr]) => true
      case invocation: MethodInvocation if invocation.applicationProblems.nonEmpty => true
      case infix: ScInfixExpr if isAssignmentOrUpdate(infix) => true
      case _: ScExpression if parent.exists(isAssignmentOrUpdate) => true
      // Warning will be reported for the parent expression
      case infix: ScInfixExpr if LogicalBinary.contains(infix.operation.refName) => parent match {
        case Some(parentInfix: ScInfixExpr) => parentInfix.operation.refName == infix.operation.refName
        case _ => false
      }
      // Sometimes we pass constant values to methods just to name them, highlighting this could be annoying
      case _: ScReferenceExpression if parent.exists(_.is[ScArgumentExprList]) => true
      // Sometimes we create constant boolean values just to name them, highlighting this could be annoying
      case _: ScReferenceExpression if value == DfaConstantValue.True || value == DfaConstantValue.False => true
      case prefix: ScPrefixExpr if prefix.getBaseExpr.is[ScReferenceExpression] &&
        value == DfaConstantValue.True || value == DfaConstantValue.False => true
      case _ => false
    }
  }

  private def isAssignmentOrUpdate(element: PsiElement): Boolean = element match {
    case infix: ScInfixExpr => infix.isAssignment || infix.isUpdateCall
    case invocation: MethodInvocation => invocation.isUpdateCall
    case _ => false
  }

  private def findProperParent(element: PsiElement): Option[PsiElement] = {
    var parent = element.parent
    while (parent match {
      case Some(_: ScParenthesisedExpr) => true
      case _ => false
    }) {
      parent = parent.get.parent
    }
    parent
  }
}
