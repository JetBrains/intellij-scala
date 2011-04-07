package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.codeInspection.AbstractFix

/**
 * Pavel Fatin
 */

class AddEmptyParentheses(f: ScFunction) extends AbstractFix("Add empty parentheses", f) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    f.addEmptyParens()
  }
}
