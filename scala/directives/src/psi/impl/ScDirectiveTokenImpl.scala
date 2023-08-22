package org.jetbrains.plugins.scalaDirective.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirectiveToken

class ScDirectiveTokenImpl(
  override val tokenType: ScalaDirectiveElementType,
  text: CharSequence
) extends LeafPsiElement(tokenType, text) with ScDirectiveToken {

  override def toString: String = s"ScDirectiveToken(${tokenType.toString})"

}
