package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.codeInspection.AbstractFix

/**
 * Pavel Fatin
 */

class RemoveEqualsSign(f: ScFunctionDefinition) extends AbstractFix("Remove redundant equals sign", f) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeAssignment()
  }
}
