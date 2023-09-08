package org.jetbrains.plugins.scalaDirective.lang

import com.intellij.patterns.PlatformPatterns.psiElement
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes

package object completion {
  private[completion] val DirectivePrefix = "//>"
  private[completion] val UsingDirective = "using"

  private[completion] val ScalaDirectiveKeyPattern = psiElement()
    .withElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY)
}
