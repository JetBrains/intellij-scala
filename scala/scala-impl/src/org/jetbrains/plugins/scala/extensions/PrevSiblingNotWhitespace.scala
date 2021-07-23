package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object PrevSiblingNotWhitespace {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getPrevSiblingNotWhitespace)
}