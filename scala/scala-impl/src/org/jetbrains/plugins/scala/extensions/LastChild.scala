package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */
object LastChild {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e.getLastChild)
}