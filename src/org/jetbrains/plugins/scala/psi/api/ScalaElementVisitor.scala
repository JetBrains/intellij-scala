package org.jetbrains.plugins.scala.psi.api

import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: ScalaPsiElement): Unit = {
    element.acceptChildren(this)
  }
}

class ScalaElementVisitor extends PsiElementVisitor {
  def visitReference(ref: ScReferenceElement) {
    visitElement(ref)
  }

  def visitElement(element: ScalaPsiElement) = super.visitElement(element)

  def visitPattern(pat: ScPattern) {}
}