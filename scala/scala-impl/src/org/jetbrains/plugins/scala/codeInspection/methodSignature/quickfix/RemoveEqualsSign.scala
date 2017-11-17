package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */
class RemoveEqualsSign(f: ScFunctionDefinition) extends AbstractFixOnPsiElement("Remove redundant equals sign", f) {

  override protected def doApplyFix(element: ScFunctionDefinition)
                                   (implicit project: Project): Unit = {
    element.removeAssignment()
  }
}
