package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * Pavel Fatin
  */
class AddEmptyParentheses(f: ScFunction) extends AbstractFixOnPsiElement("Add empty parentheses", f) {

  override protected def doApplyFix(element: ScFunction)
                                   (implicit project: Project): Unit = {
    import ScalaPsiElementFactory.createClauseFromText
    element.paramClauses.addClause(createClauseFromText())
  }
}
