package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

final class RemoveExpressionQuickFix(expression: ScExpression)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.expression"), expression) {

  override protected def doApplyFix(expression: ScExpression)(implicit project: Project): Unit = expression.delete()

}
