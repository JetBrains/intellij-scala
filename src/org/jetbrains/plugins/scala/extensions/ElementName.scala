package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiNamedElement

/**
 * Pavel Fatin
 */

object ElementName {
  def unapply(e: PsiNamedElement): Some[String] = Some(e.name)
}