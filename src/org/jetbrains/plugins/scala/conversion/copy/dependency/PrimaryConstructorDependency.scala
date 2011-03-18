package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class PrimaryConstructorDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency with Cloneable {
  override def clone() = new PrimaryConstructorDependency(startOffset, endOffset, className)
}

object PrimaryConstructorDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new PrimaryConstructorDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}