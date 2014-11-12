package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Pavel Fatin
 */

class RemoveCallParentheses(call: ScMethodCall) extends AbstractFix("Remove call parentheses", call) {
  def doApplyFix(project: Project) {
    val text = call.getInvokedExpr.getText
    val exp = ScalaPsiElementFactory.createExpressionFromText(text, call.getManager)
    call.replace(exp)
  }
}
