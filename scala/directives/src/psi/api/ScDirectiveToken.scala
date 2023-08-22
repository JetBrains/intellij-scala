package org.jetbrains.plugins.scalaDirective
package psi.api

import lang.lexer.ScalaDirectiveElementType

import com.intellij.psi.PsiElement

trait ScDirectiveToken extends PsiElement {
  def tokenType: ScalaDirectiveElementType
}

