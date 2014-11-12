package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class AddEmptyParentheses(f: ScFunction) extends AbstractFix("Add empty parentheses", f) {
  def doApplyFix(project: Project) {
    f.addEmptyParens()
  }
}
