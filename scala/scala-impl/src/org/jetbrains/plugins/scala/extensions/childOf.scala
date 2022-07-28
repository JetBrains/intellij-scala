package org.jetbrains.plugins.scala
package extensions

import com.intellij.psi.PsiElement

object childOf {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = {
    if (elem != null && elem.getParent != null) Some(elem, elem.getParent)
    else None
  }
}
