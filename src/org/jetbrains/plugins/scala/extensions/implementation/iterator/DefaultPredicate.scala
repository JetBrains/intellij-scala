package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 21.05.2010
 */

object DefaultPredicate extends Function[PsiElement, Boolean] {
  def apply(e: PsiElement) = true 
}