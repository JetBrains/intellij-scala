package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiMember, PsiNamedElement}

object Member {
  def unapply(e: PsiMember with PsiNamedElement): Option[(String, String)] =
    Option(e.containingClass).map(it => (e.name, it.qualifiedName))
}