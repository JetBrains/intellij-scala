package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object NextSibling {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getNextSibling)
}