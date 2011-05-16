package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class PatternDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency with Cloneable {
  override def clone() = new TypeDependency(startOffset, endOffset, className)
}

object PatternDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new PatternDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}