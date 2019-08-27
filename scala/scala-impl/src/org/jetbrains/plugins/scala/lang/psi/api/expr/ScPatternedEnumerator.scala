package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement

trait ScPatternedEnumerator extends ScPatterned {

  // only can exist in for-binding, but added here in order annotator can highlight the error, not the parser
  def valKeyword: Option[PsiElement]
}
