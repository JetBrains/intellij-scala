package org.jetbrains.plugins.scala.lang.scalacli.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliElementType
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.ScCliDirectiveToken

class ScCliDirectiveTokenImpl(
  override val tokenType: ScalaCliElementType,
  text: CharSequence
) extends LeafPsiElement(tokenType, text) with ScCliDirectiveToken {

  override def toString: String = s"ScCliDirectiveToken(${tokenType.toString})"

}
