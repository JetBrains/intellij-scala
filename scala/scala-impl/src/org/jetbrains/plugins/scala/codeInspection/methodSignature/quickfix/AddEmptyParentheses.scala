package org.jetbrains.plugins.scala
package codeInspection
package methodSignature
package quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class AddEmptyParentheses(function: ScFunction)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("empty.parentheses"), function) {

  override protected def doApplyFix(function: ScFunction)
                                   (implicit project: Project): Unit = {
    import ScalaPsiElementFactory.createClauseFromText
    function.paramClauses.addClause(createClauseFromText())
  }
}
