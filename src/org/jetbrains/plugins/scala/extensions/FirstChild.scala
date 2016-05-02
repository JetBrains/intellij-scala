package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */
object FirstChild {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getFirstChild)
}