package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class PackageDependency(startOffset: Int, endOffset: Int, packageName: String) extends Dependency {
  def movedTo(startOffset: Int, endOffset: Int) =
    new PackageDependency(startOffset, endOffset, packageName)

  def path(wildchardMembers: Boolean) = packageName
}

object PackageDependency {
  def apply(element: PsiElement, startOffset: Int, packageName: String) = {
    val range = element.getTextRange
    new PackageDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, packageName)
  }
}