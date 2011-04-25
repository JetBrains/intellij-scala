package org.jetbrains.plugins.scala
package codeInspection
package postfix

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.{ScInfixExpr, ScParenthesisedExpr, ScPostfixExpr}

class PostfixMethodCall extends AbstractInspection("UseOfPostfixMethodCall", "Use of postfix method call"){
  @Language("HTML")
  val description =
"""Postfix method invokation, <code>f a</code>, can interfere with semicolon inference.
It is <a href="http://twitter.com/#!/odersky/status/49882758968905728">recommended</a> to use <code>f.a</code> instead."""

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case pexpr: ScPostfixExpr if !safe(pexpr) =>
      holder.registerProblem(pexpr, getDisplayName, new AddDotFix(pexpr))
  }

  private def safe(pexpr: ScPostfixExpr): Boolean = {
    pexpr.getContext match {
      case _: ScParenthesisedExpr => true
      case _ =>
        val followedBySemicolon = Option(pexpr.getNextSibling).map(_.getNode.getElementType) == Some(ScalaTokenTypes.tSEMICOLON)
        followedBySemicolon
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
