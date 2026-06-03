package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class AddEmptyParentheses(function: ScFunction)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("empty.parentheses"), function) with DumbAware {

  override protected def doApplyFix(function: ScFunction)
                                   (implicit project: Project): Unit = {
    import ScalaPsiElementFactory.createClauseFromText
    function.paramClauses.addClause(createClauseFromText(features = function))
  }
}
