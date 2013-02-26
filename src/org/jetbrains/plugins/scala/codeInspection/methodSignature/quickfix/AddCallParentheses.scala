package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * Pavel Fatin
 */

class AddCallParentheses(e: ScExpression) extends AbstractFix("Add call parentheses", e) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!e.isValid) return
    val exprToFix = e.getParent match {
      case postf: ScPostfixExpr => postf
      case call: ScGenericCall => call
      case _ => e
    }
    val text = s"${exprToFix.getText}()"
    val call = ScalaPsiElementFactory.createExpressionFromText(text, e.getManager)
    exprToFix.replace(call)
  }
}

class AddGenericCallParentheses(e: ScGenericCall) extends AbstractFix("Add call parentheses", e) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!e.isValid) return
    val text = s"${e.getText}()"
    val call = ScalaPsiElementFactory.createExpressionFromText(text, e.getManager)
    e.replace(call)
  }
}
