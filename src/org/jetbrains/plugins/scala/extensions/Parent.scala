package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

object Parent {
  def unapply(e: PsiElement): Option[PsiElement] = Option(e).map(_.getParent)
}