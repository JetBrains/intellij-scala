package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class MemberDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency with Cloneable {
  override def clone() = new MemberDependency(startOffset, endOffset, className, memberName)
}

object MemberDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new MemberDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}
