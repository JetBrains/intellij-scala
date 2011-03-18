package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class PackageDependency(startOffset: Int, endOffset: Int, packageName: String) extends Dependency with Cloneable {
  override def clone() = new PackageDependency(startOffset, endOffset, packageName)
}

object PackageDependency {
  def apply(element: PsiElement, startOffset: Int, packageName: String) = {
    val range = element.getTextRange
    new PackageDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, packageName)
  }
}