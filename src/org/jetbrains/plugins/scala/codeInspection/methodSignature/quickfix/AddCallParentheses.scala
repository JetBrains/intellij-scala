package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Pavel Fatin
 */

class AddCallParentheses(e: ScReferenceExpression) extends LocalQuickFix {
  def getName = "Add call parentheses"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    val text = "%s()".format(e.getText)
    val call = ScalaPsiElementFactory.createExpressionFromText(text, e.getManager)
    e.replace(call)
  }
}
