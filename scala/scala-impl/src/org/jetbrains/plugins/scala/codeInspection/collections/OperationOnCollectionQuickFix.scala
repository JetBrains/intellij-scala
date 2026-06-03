package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class OperationOnCollectionQuickFix(
  @Nls override val getFamilyName: String,
  expression: ScExpression,
  replacementText: String,
) extends PsiUpdateModCommandAction[ScExpression](expression) {
  override def invoke(context: ActionContext, expression: ScExpression, updater: ModPsiUpdater): Unit = {
    val replacement = createExpressionFromText(replacementText, expression)(context.project())
    expression.replaceExpression(replacement, removeParenthesis = true)
  }
}

object OperationOnCollectionQuickFix {

  def apply(simplification: Simplification): OperationOnCollectionQuickFix = {
    val Simplification(toReplace, replacementText, hint, _) = simplification
    new OperationOnCollectionQuickFix(hint, toReplace.getElement, replacementText)
  }
}
