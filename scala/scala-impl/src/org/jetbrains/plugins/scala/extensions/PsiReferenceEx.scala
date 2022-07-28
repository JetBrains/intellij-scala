package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}

object PsiReferenceEx {
  object resolve {
    def unapply(e: PsiReference): Option[PsiElement] = Option(e.resolve)
  }
}