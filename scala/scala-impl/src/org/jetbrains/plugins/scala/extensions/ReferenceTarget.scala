package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}

object ReferenceTarget {
  def unapply(e: PsiReference): Option[PsiElement] = Option(e.resolve)
}