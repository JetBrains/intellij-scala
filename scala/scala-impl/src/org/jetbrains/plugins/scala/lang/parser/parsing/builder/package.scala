package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.IndentationWidth

import scala.annotation.tailrec

package object builder {
  private[builder] def lookBehindForPrecedingIndentation(builder: ScalaPsiBuilder, stepOfCurrentToken: Int): Option[IndentationWidth] = {
    val originalText = builder.getOriginalText

    @tailrec
    def inner(step: Int, tokenEnd: Int, lastNonWsStart: Int): Option[IndentationWidth] = {
      builder.rawLookup(step) match {
        case c if ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(c) =>
          val commentStart = builder.rawTokenTypeStart(step)
          inner(step - 1, commentStart, commentStart)
        case ws if ScalaTokenTypes.WHITES_SPACES_TOKEN_SET.contains(ws) =>
          val wsStart = builder.rawTokenTypeStart(step)
          val wsText = originalText.subSequence(wsStart, tokenEnd)
          lastNewLineOffset(wsText) match {
            case Some(newlineOffset) =>
              val indentWs = originalText.subSequence(wsStart + newlineOffset + 1, lastNonWsStart)
              Some(new IndentationWidth(indentWs.toString))
            case None =>
              inner(step - 1, wsStart, lastNonWsStart)
          }
        case _ =>
          None
      }
    }

    val currentTokenStart = builder.rawTokenTypeStart(stepOfCurrentToken)
    inner(stepOfCurrentToken - 1, currentTokenStart, currentTokenStart)
  }

  private def lastNewLineOffset(charSeq: CharSequence): Option[Int] = {
    var i = charSeq.length - 1
    while (i >= 0) {
      charSeq.charAt(i) match {
        case '\n' => return Some(i)
        case _    => i -= 1
      }
    }
    None
  }
}
