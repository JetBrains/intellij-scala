package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotation(f: ScFunctionDeclaration) extends AbstractFix("Remove redundant type annotation", f) {
  def doApplyFix(project: Project) {
    f.removeExplicitType()
  }
}
