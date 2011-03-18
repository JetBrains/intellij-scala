package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiMember, PsiClass, PsiElement}

/**
 * Pavel Fatin
 */

object ContainingClass {
  def unapply(e: PsiMember): Option[PsiClass] = {
    if (e == null) {
      None
    } else {
      val aClass = e.getContainingClass
      if (aClass == null) None else Some(aClass)
    }
  }
}