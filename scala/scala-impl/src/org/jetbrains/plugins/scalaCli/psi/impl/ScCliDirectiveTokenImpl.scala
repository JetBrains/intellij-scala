package org.jetbrains.plugins.scalaCli.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scalaCli.lang.lexer.ScalaCliElementType
import org.jetbrains.plugins.scalaCli.psi.api.ScCliDirectiveToken

class ScCliDirectiveTokenImpl(
  override val tokenType: ScalaCliElementType,
  text: CharSequence
) extends LeafPsiElement(tokenType, text) with ScCliDirectiveToken {

  override def toString: String = s"ScCliDirectiveToken(${tokenType.toString})"

}
