package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiClass

/**
 * Pavel Fatin
 */

object ClassQualifiedName {
  def unapply(e: PsiClass): Some[String] = Some(e.qualifiedName)
}