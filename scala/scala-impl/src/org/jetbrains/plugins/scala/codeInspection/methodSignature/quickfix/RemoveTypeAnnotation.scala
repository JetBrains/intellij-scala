package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotation(f: ScFunctionDeclaration) extends AbstractFixOnPsiElement("Remove redundant type annotation", f) {

  override protected def doApplyFix(element: ScFunctionDeclaration)
                                   (implicit project: Project): Unit = {
    element.removeExplicitType()
  }
}
