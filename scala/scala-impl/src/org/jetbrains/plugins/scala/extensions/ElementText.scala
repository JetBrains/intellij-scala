package org.jetbrains.plugins.scala
package extensions

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

object ElementText {
  def unapply(e: PsiElement): Some[String] = Some(e.getText)
}