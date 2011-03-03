package org.jetbrains.plugins.scala.codeInspection.methodSignature

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}

/**
 * Pavel Fatin
 */

class AddParensQuickFix(f: ScFunction) extends LocalQuickFix {
  def getName = "Add parens"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    f.addParens()
  }
}
