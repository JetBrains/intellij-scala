package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, literals}

final class ScCharLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends QuotedLiteralImplBase(node, toString) with literals.ScCharLiteral {

  override protected def startQuote: String = ScLiteral.CharQuote

  override def getValue: Character = super.getValue match {
    case chars: String =>
      val outChars = new java.lang.StringBuilder
      val success = PsiLiteralExpressionImpl.parseStringCharacters(
        chars,
        outChars,
        null
      )

      if (success && outChars.length == 1) Character.valueOf(outChars.charAt(0))
      else null
    case _ => null
  }
}
