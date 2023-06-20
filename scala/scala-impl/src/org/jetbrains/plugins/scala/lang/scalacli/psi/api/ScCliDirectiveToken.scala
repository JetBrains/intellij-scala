package org.jetbrains.plugins.scala.lang.scalacli.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliElementType

trait ScCliDirectiveToken extends PsiElement {
  def tokenType: ScalaCliElementType
}

