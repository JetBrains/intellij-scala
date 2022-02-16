package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiNamedElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

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

  private def decoratedQualifiedName(element: PsiNamedElement, owner: PsiElement, pathToOwner: String)
                                    (function: (PsiElement, String) => String): String = {
    val prefix = function(owner, pathToOwner)
    val suffix = function(element, element.name)
    prefix + "." + suffix
  }

  def withDeprecations(element: PsiNamedElement, owner: PsiElement, pathToOwner: String): String =
    decoratedQualifiedName(element, owner, pathToOwner) {
      case (member: PsiDocCommentOwner, string) if member.isDeprecated => asDeprecated(string)
      case (_, string)                                                 => string
    }

  def withDeprecation(psiClass: PsiClass): String = {
    if (psiClass.isDeprecated) asDeprecated(psiClass.qualifiedName)
    else psiClass.qualifiedName
  }

}
