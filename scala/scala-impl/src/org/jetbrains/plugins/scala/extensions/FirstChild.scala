package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object FirstChild {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getFirstChild)
}