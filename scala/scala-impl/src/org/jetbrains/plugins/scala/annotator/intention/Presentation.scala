package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiNamedElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, PsiClassExt, PsiNamedElementExt}

object Presentation {
  //noinspection ScalaExtractStringToBundle
  @Nls
  def htmlWithBody(@Nls text: String): String =
    s"<html><body>$text</body></html>"

  private def asDeprecated(qualifiedName: String): String = {
    val nameStartIndex = qualifiedName.lastIndexOf('.') + 1
    val prefix = qualifiedName.substring(0, nameStartIndex)
    val name = qualifiedName.substring(nameStartIndex)
    s"$prefix<s>$name</s>"
  }

  private def decoratedQualifiedName(element: PsiNamedElement, owner: PsiClass)
                                    (function: (PsiElement, String) => String): String = owner match {
    case ClassQualifiedName(qualifiedName) if qualifiedName.nonEmpty =>
      val prefix = function(owner, qualifiedName)
      val suffix = function(element, element.name)
      prefix + "." + suffix
    case _ =>
      function(element, element.name)
  }

  def withDeprecations(element: PsiNamedElement, owner: PsiClass): String =
    decoratedQualifiedName(element, owner) {
      case (member: PsiDocCommentOwner, string) if member.isDeprecated => asDeprecated(string)
      case (_, string)                                                 => string
    }

  def withDeprecation(psiClass: PsiClass): String = {
    if (psiClass.isDeprecated) asDeprecated(psiClass.qualifiedName)
    else psiClass.qualifiedName
  }

}
