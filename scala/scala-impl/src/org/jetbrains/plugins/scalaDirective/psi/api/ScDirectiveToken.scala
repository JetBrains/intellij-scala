package org.jetbrains.plugins.scalaDirective.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType

trait ScDirectiveToken extends PsiElement {
  def tokenType: ScalaDirectiveElementType
}

