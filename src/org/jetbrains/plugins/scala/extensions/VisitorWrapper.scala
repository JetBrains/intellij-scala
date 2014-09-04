package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
 * Pavel Fatin
 */

class VisitorWrapper(action: PartialFunction[PsiElement, Any]) extends PsiElementVisitor {
  override def visitElement(element: PsiElement) {
    if (action.isDefinedAt(element)) action(element)
  }
}

object VisitorWrapper {
  def apply(action: PartialFunction[PsiElement, Any]) = new VisitorWrapper(action)
}
