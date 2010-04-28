package org.jetbrains.plugins

import scala.lang.psi.RichPsiElement

import com.intellij.psi.{PsiElement, PsiReference}
/**
 * Pavel.Fatin, 21.04.2010
 */

package object scala {
  implicit def toRichObject[T](o: T) = new RichObject[T](o)

  implicit def toRichPsiElement(e: PsiElement) = new RichPsiElement(e)

  class RichObject[T](v: T) {
    def toOption: Option[T] = if (v == null) None else Some(v)
  }

  object Parent {
    def unapply(e: PsiElement) = {
      if (e == null) {
        None
      } else {
        val parent = e.getParent
        if (parent == null) None else Some(parent)
      }
    }
  }

  object Resolved {
    def unapply(e: PsiReference) = {
      if (e == null) {
        None
      } else {
        val target = e.resolve
        if (target == null) None else Some(target)
      }
    }
  }
}

