package org.jetbrains.plugins.scala
package codeInspection
package postfix

import com.intellij.psi.PsiElement
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import extensions.childOf

class PostfixMethodCallInspection extends AbstractInspection("UseOfPostfixMethodCall", "Use of postfix method call"){

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case pexpr: ScPostfixExpr if !safe(pexpr) =>
      holder.registerProblem(pexpr, getDisplayName, new AddDotFix(pexpr))
  }

  private def safe(pexpr: ScPostfixExpr): Boolean = {
    pexpr.getContext match {
      case _: ScParenthesisedExpr => true
      case _: ScArgumentExprList => true
      case (_: ScAssignStmt) childOf (_: ScArgumentExprList) => true //named arguments
      case _ =>
        val next = pexpr.getNextSiblingNotWhitespace
        if (next == null) return false
        val nextNode = next.getNode
        if (nextNode == null) return false
        nextNode.getElementType == ScalaTokenTypes.tSEMICOLON
    }
  }
}

class AddDotFix(pexpr: ScPostfixExpr) extends AbstractFix("Add dot to method call", pexpr) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    val operand = pexpr.operand
    val needParens = operand match {
      case lit: ScLiteral if lit.getText.endsWith(".") => true
      case _: ScInfixExpr => true
      case _ => false // TODO others?
    }
    val operandText = if (needParens) "(" + operand.getText + ")" else operand.getText
    val call = operandText + "." + pexpr.operation.getText
    val exp = ScalaPsiElementFactory.createExpressionFromText(call, pexpr.getManager)
    pexpr.replace(exp)
  }
}
