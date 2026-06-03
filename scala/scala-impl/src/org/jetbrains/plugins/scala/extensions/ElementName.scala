package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiNamedElement

object ElementName {
  def unapply(e: PsiNamedElement): Some[String] = Some(e.name)
}