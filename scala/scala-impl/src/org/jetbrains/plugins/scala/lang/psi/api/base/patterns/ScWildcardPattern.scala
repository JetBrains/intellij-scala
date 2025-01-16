package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.psi.PsiElement

trait ScWildcardPattern extends ScPattern {
  def underscoreToken: PsiElement
}