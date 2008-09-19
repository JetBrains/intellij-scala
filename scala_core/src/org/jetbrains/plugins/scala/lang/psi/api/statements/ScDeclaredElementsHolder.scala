package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiNamedElement

trait ScDeclaredElementsHolder extends ScalaPsiElement {
  def declaredElements : Seq[PsiNamedElement]
}