package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 5/28/13
 */
class OperationOnCollectionQuickFix(expr: ScExpression, simpl: Simplification) extends AbstractFixOnPsiElement(simpl.hint, expr){
  def doApplyFix(project: Project) {
    val toReplace = simpl.exprToReplace.getElement
    if (!toReplace.isValid) return
    val newExpr = ScalaPsiElementFactory.createExpressionFromText(simpl.replacementText, toReplace.getManager)
    toReplace.replaceExpression(newExpr, removeParenthesis = true)
  }
}
