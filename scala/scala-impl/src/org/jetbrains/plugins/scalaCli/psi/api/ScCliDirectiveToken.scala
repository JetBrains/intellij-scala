package org.jetbrains.plugins.scalaCli.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scalaCli.lang.lexer.ScalaCliElementType

trait ScCliDirectiveToken extends PsiElement {
  def tokenType: ScalaCliElementType
}

