package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotation(f: ScFunctionDeclaration) extends AbstractFixOnPsiElement("Remove redundant type annotation", f) {
  def doApplyFix(project: Project) {
    getElement.removeExplicitType()
  }
}
