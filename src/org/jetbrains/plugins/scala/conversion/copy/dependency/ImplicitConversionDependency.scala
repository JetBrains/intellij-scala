package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class ImplicitConversionDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency with Cloneable {
  override def clone() = new ImplicitConversionDependency(startOffset, endOffset, className, memberName)
}

object ImplicitConversionDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new ImplicitConversionDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}


