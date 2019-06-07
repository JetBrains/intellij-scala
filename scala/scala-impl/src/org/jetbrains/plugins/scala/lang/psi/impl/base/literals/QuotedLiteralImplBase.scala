package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

abstract class QuotedLiteralImplBase(node: ASTNode,
                                     override val toString: String)
  extends ScLiteralImplBase(node, toString) {

  protected def startQuote: String

  protected def endQuote: String = startQuote

  override def getValue: AnyRef =
    QuotedLiteralImplBase.trimQuotes(getText, startQuote)(endQuote)

  override final def contentRange: TextRange = {
    val range = super.contentRange
    new TextRange(
      range.getStartOffset + startQuote.length,
      range.getEndOffset - endQuote.length
    )
  }
}

object QuotedLiteralImplBase {

  // TODO supposed to be getValue implementation
  private[base] def trimQuotes(text: String, startQuote: String)
                              (endQuote: String = startQuote) =
    if (text.startsWith(startQuote)) {
      val beginIndex = startQuote.length
      val endIndex = text.length - (if (text.endsWith(endQuote)) endQuote.length else 0)

      if (endIndex < beginIndex) null
      else text.substring(beginIndex, endIndex)
    } else {
      null
    }
}
