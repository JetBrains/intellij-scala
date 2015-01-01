package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Nikolay.Tropin
 * 2014-09-22
 */
class RemoveElementQuickFix(name: String, e: PsiElement) extends AbstractFixOnPsiElement(name, e) {
  override def doApplyFix(project: Project): Unit = {
    getElement.delete()
  }
}
