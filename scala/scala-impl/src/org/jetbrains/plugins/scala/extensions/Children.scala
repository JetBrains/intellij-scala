package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement

object Children {
  def unapplySeq(e: PsiElement): Some[Seq[PsiElement]] = Some(e.getChildren.toSeq)
}