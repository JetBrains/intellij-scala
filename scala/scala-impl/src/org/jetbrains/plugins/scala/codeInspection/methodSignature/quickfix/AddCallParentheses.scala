package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * Pavel Fatin
 */

class AddCallParentheses(e: ScExpression) extends AbstractFixOnPsiElement("Add call parentheses", e) {

  override protected def doApplyFix(expr: ScExpression)
                                   (implicit project: Project): Unit = {
    val exprToFix = expr.getParent match {
      case postf: ScPostfixExpr => postf
      case call: ScGenericCall => call
      case _ => expr
    }
    val text = s"${exprToFix.getText}()"
    val call = createExpressionFromText(text)
    exprToFix.replace(call)
  }
}

class AddGenericCallParentheses(e: ScGenericCall) extends AbstractFixOnPsiElement("Add call parentheses", e) {

  override protected def doApplyFix(expr: ScGenericCall)
                                   (implicit project: Project): Unit = {
    val text = s"${expr.getText}()"
    val call = createExpressionFromText(text)
    expr.replace(call)
  }
}
