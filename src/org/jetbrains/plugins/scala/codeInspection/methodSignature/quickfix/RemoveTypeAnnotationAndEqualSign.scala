package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotationAndEqualSign(f: ScFunctionDefinition) extends LocalQuickFix {
  def getName = "Remove redundant type annotation and equals sign"

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeExplicitType()
    f.removeAssignment()
  }
}
