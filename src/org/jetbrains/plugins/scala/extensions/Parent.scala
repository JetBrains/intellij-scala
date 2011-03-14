package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

object Parent {
  def unapply(e: PsiElement): Option[PsiElement] = {
    if (e == null) {
      None
    } else {
      val parent = e.getParent
      if (parent == null) None else Some(parent)
    }
  }
}