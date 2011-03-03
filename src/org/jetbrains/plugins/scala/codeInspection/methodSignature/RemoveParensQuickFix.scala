package org.jetbrains.plugins.scala.codeInspection.methodSignature

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}

/**
 * Pavel Fatin
 */

class RemoveParensQuickFix(f: ScFunction) extends LocalQuickFix {
  def getName = "Remove parens"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeAllClauses()
  }
}
