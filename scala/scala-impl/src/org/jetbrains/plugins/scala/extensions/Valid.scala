package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object Valid {
  def unapply(element: PsiElement): Option[PsiElement] = Option(element).filter(_.isValid)
}
