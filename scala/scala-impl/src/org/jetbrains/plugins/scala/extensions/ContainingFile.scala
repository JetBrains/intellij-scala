package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiFile, PsiMember}

object ContainingFile {
  def unapply(e: PsiMember): Option[PsiFile] =
    Option(e.getContainingFile)
}