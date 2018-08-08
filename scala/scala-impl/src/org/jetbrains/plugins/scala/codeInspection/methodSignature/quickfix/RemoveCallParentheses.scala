package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * Pavel Fatin
 */
class RemoveCallParentheses(call: ScMethodCall) extends AbstractFixOnPsiElement("Remove call parentheses", call) {

  override protected def doApplyFix(mCall: ScMethodCall)
                                   (implicit project: Project): Unit = {
    val text = mCall.getInvokedExpr.getText
    mCall.replace(createExpressionFromText(text))
  }
}
