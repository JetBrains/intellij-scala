package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class TypeDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency with Cloneable {
  override def clone() = new TypeDependency(startOffset, endOffset, className)
}

object TypeDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new TypeDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}