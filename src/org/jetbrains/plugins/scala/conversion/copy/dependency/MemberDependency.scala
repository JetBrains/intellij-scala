package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class MemberDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency {
  def movedTo(startOffset: Int, endOffset: Int) =
    new MemberDependency(startOffset, endOffset, className, memberName)

  def path(wildchardMembers: Boolean) = "%s.%s".format(className, if (wildchardMembers) "_" else memberName)
}

object MemberDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new MemberDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}
