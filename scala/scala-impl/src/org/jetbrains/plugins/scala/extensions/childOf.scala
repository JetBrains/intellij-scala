package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object childOf {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = {
    if (elem != null && elem.getParent != null) Option(elem, elem.getParent)
    else                                        None
  }
}

object contextChildOf {
  def unapply(elem: PsiElement): Option[(PsiElement, PsiElement)] = {
    if (elem != null && elem.getContext != null) Option(elem, elem.getContext)
    else                                         None
  }
}
