package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.psi.{PsiElement, PsiNamedElement}

private object NavigationElementUtils {

  def describeTarget(element: PsiElement): String = {
    val nav = element.getNavigationElement
    val originalText = s"${describeElement(element)}"
    val navigationText = s"${describeElement(nav)}"
    s"$originalText -> nav:$navigationText"
  }

  def describeElement(element: PsiElement): String = {
    val name = element match {
      case named: PsiNamedElement => named.getName
      case _ => element.toString
    }
    val className = element.getClass.getName
    s"$name@${elementLocationPath(element)}[$className]"
  }

  def elementLocationPath(element: PsiElement): String = {
    val file = element.getContainingFile
    file.getViewProvider.getVirtualFile.getPath
  }
}
