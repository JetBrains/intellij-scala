package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}

/**
 * Pavel Fatin
 */

class AddEmptyParentheses(f: ScFunction) extends LocalQuickFix {
  def getName = "Add empty parentheses"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    f.addEmptyParens()
  }
}
