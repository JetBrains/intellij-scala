package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class OperationOnCollectionQuickFix(@Nls hint: String,
                                    expression: ScExpression,
                                    replacementText: String) extends AbstractFixOnPsiElement(hint, expression) {

  override protected def doApplyFix(expression: ScExpression)
                                   (implicit project: Project): Unit = {
    val replacement = createExpressionFromText(replacementText)
    expression.replaceExpression(replacement, removeParenthesis = true)
  }
}

object OperationOnCollectionQuickFix {

  def apply(simplification: Simplification): OperationOnCollectionQuickFix = {
    val Simplification(toReplace, replacementText, hint, _) = simplification
    new OperationOnCollectionQuickFix(hint, toReplace.getElement, replacementText)
  }
}
