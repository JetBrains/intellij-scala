package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

trait ScPatternedEnumeratorBase extends ScPatternedBase { this: ScPatternedEnumerator =>

  // only can exist in for-binding, but always parsed
  def valKeyword: Option[PsiElement]

  // only can exist in generator, but always parsed
  def caseKeyword: Option[PsiElement]
}