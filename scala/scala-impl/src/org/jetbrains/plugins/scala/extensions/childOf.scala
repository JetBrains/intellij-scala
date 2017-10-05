package org.jetbrains.plugins.scala
package extensions

import com.intellij.psi.PsiElement

/**
 * Nikolay.Tropin
 * 9/26/13
 */
object childOf {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = {
    if (elem != null && elem.getParent != null) Some(elem, elem.getParent)
    else None
  }
}
