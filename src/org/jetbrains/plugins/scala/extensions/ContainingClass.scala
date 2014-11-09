package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiClass, PsiMember}

/**
 * Pavel Fatin
 */

object ContainingClass {
  def unapply(e: PsiMember): Option[PsiClass] = {
    if (e == null) {
      None
    } else {
      val aClass = e.containingClass
      if (aClass == null) None else Some(aClass)
    }
  }
}