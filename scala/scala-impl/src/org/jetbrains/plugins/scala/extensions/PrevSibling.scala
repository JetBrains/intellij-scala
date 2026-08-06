package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object PrevSibling {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getPrevSibling)
}