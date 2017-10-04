package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}

/**
 * Pavel Fatin
 */

object PsiReferenceEx {
  object resolve {
    def unapply(e: PsiReference): Option[PsiElement] = Option(e.resolve)
  }
}