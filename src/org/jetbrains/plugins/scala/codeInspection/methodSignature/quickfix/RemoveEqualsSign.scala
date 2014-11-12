package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class RemoveEqualsSign(f: ScFunctionDefinition) extends AbstractFix("Remove redundant equals sign", f) {
  def doApplyFix(project: Project) {
    f.removeAssignment()
  }
}
