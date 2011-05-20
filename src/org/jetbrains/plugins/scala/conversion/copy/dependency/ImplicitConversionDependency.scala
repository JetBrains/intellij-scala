package org.jetbrains.plugins.scala.conversion.copy.dependency

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class ImplicitConversionDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency  {
  def movedTo(startOffset: Int, endOffset: Int) =
    new ImplicitConversionDependency(startOffset, endOffset, className, memberName)

  def path(wildchardMembers: Boolean) = "%s._".format(className)
}

object ImplicitConversionDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new ImplicitConversionDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}


