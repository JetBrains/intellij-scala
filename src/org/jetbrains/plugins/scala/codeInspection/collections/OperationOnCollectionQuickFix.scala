package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 5/28/13
 */
class OperationOnCollectionQuickFix(expr: ScExpression, simpl: Simplification) extends AbstractFix(simpl.hint, expr){
  def doApplyFix(project: Project) {
    if (!expr.isValid) return
    val newExpr = ScalaPsiElementFactory.createExpressionFromText(simpl.replacementText, expr.getManager)
    expr.replaceExpression(newExpr, removeParenthesis = true)
  }
}
