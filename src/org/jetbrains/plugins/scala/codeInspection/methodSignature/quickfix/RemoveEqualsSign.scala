package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class RemoveEqualsSign(f: ScFunctionDefinition) extends AbstractFixOnPsiElement("Remove redundant equals sign", f) {
  def doApplyFix(project: Project) {
    getElement.removeAssignment()
  }
}
