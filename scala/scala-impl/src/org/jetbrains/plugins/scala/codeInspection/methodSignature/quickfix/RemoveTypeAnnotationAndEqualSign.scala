package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotationAndEqualSign(f: ScFunctionDefinition) extends AbstractFixOnPsiElement("Remove redundant type annotation and equals sign", f) {
  def doApplyFix(project: Project) {
    val funDef = getElement
    funDef.removeExplicitType()
    funDef.removeAssignment()
  }
}
