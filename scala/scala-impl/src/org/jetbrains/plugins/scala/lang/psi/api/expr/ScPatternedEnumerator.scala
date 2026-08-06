package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement

trait ScPatternedEnumerator extends ScPatterned {

  // only can exist in for-binding, but always parsed
  def valKeyword: Option[PsiElement]

  // only can exist in generator, but always parsed
  def caseKeyword: Option[PsiElement]
}
