package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiNamedElement}
import com.intellij.util.SlowOperations
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

import scala.util.Using

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
      case (member: PsiDocCommentOwner, string) if isDeprecated(member) => asDeprecated(string)
      case (_, string)                                                  => string
    }

  def withDeprecation(element: PsiElement, path: String): String = element match {
    case member: PsiDocCommentOwner if isDeprecated(member) => asDeprecated(path)
    case _                                                  => path
  }

  def withDeprecation(psiClass: PsiClass): String = {
    if (isDeprecated(psiClass)) asDeprecated(psiClass.qualifiedName)
    else psiClass.qualifiedName
  }

  private def isDeprecated(element: PsiDocCommentOwner): Boolean = Using.resource(SlowOperations.knownIssue("SCL-23057")) { _ =>
    element.isDeprecated
  }
}
