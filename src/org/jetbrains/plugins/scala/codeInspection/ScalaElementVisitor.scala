package org.jetbrains.plugins.scala
package codeInspection


import com.intellij.psi.{PsiElementVisitor, PsiElement}
import lang.psi.api.base.ScReferenceElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class ScalaElementVisitor extends PsiElementVisitor {
  def visitReference(ref: ScReferenceElement) {
    visitElement(ref)
  }
}

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: PsiElement): Unit = {
    element.acceptChildren(this)
  }
}