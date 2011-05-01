package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScMethodCall, ScReferenceExpression}

/**
 * Pavel Fatin
 */

class AddCallParentheses(e: ScReferenceExpression) extends AbstractFix("Add call parentheses", e) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!e.isValid) return
    val text = "%s()".format(e.getText)
    val call = ScalaPsiElementFactory.createExpressionFromText(text, e.getManager)
    e.replace(call)
  }
}

class AddGenericCallParentheses(e: ScGenericCall) extends AbstractFix("Add call parentheses", e) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!e.isValid) return
    val text = "%s()".format(e.getText)
    val call = ScalaPsiElementFactory.createExpressionFromText(text, e.getManager)
    e.replace(call)
  }
}
