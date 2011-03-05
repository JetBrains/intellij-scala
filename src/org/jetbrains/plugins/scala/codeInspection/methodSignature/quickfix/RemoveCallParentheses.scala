package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Pavel Fatin
 */

class RemoveCallParentheses(call: ScMethodCall) extends LocalQuickFix {
  def getName = "Remove call parentheses"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    val text = call.getInvokedExpr.getText
    val exp = ScalaPsiElementFactory.createExpressionFromText(text, call.getManager)
    call.replace(exp)
  }
}
