package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object PrevElement {
  def unapply(e: PsiElement): Option[PsiElement] = e.prevElement
}
