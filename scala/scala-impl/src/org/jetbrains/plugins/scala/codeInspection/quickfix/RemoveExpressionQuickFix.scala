package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

final class RemoveExpressionQuickFix(expression: ScExpression) extends PsiUpdateModCommandAction[ScExpression](expression) {
  override def getFamilyName: String = ScalaInspectionBundle.message("remove.expression")

  override def invoke(context: ActionContext, expression: ScExpression, updater: ModPsiUpdater): Unit =
    expression.delete()
}
