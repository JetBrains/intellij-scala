package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * @author Nikolay.Tropin
 */
class ChangeReferenceNameQuickFix(name: String, ref: ScReferenceElement, newRefName: String)
        extends AbstractFixOnPsiElement(name, ref) {

  override protected def doApplyFix(element: ScReferenceElement)
                                   (implicit project: Project): Unit = {
    element.handleElementRename(newRefName)
  }
}
