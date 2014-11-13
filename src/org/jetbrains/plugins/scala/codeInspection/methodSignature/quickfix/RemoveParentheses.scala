package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class RemoveParentheses(f: ScFunction) extends AbstractFixOnPsiElement("Remove redundant parentheses", f) {
  def doApplyFix(project: Project) {
    getElement.removeAllClauses()
  }
}
