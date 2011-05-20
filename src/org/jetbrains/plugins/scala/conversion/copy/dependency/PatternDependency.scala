package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class PatternDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency {
  def movedTo(startOffset: Int, endOffset: Int) =
    new TypeDependency(startOffset, endOffset, className)

  def path(wildchardMembers: Boolean) = className
}

object PatternDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new PatternDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}