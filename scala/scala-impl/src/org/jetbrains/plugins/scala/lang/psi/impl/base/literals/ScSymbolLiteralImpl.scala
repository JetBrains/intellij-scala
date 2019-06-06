package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral

final class ScSymbolLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with ScSymbolLiteral {

  import ScLiteralImpl._

  private def startQuote: String = CharQuote

  private def endQuote: String = ""

  override def getValue: Symbol = trimQuotes(getText, startQuote)(endQuote) match {
    case null => null
    case symbolText => Symbol(symbolText)
  }

  override def contentRange: TextRange = {
    val range = super.contentRange
    new TextRange(
      range.getStartOffset + startQuote.length,
      range.getEndOffset - endQuote.length
    )
  }
}
