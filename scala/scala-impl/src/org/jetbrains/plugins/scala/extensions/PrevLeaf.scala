package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

object PrevLeaf {
  def unapply(e: PsiElement): Option[PsiElement] =
    Option(PsiTreeUtil.prevLeaf(e))
}