package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Nikolay.Tropin
 */
class ChangeReferenceNameQuickFix(name: String, ref: ScReferenceElement, newRefName: String)
        extends AbstractFixOnPsiElement(name, ref) {
  override def doApplyFix(project: Project): Unit = {
    getElement.handleElementRename(newRefName)
  }
}
