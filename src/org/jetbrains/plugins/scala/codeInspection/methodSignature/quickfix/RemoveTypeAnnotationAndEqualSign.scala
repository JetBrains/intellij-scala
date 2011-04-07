package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.codeInspection.AbstractFix

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotationAndEqualSign(f: ScFunctionDefinition) extends AbstractFix("Remove redundant type annotation and equals sign", f) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeExplicitType()
    f.removeAssignment()
  }
}
