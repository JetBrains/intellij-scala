package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotation(f: ScFunctionDeclaration) extends LocalQuickFix {
  def getName = "Remove redundant type annotation"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeExplicitType()
  }
}
