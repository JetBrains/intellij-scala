package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * ContextBounds ::=  `:` ContextBound { `:` ContextBound }          to be deprecated eventually
 *                  | `:` `{` ContextBound { `,` ContextBound } `}`  3.6+ only
 *
 */
object ContextBounds {
  def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tCOLON) false
    else {
      builder.advanceLexer() // `:`

      val isNewStyle =
        if (builder.features.`new context bounds and givens` &&
          builder.getTokenType == ScalaTokenTypes.tLBRACE) {
          builder.advanceLexer() // `{`
          true
        } else false

      while (ContextBound()) {
        if (builder.getTokenType == ScalaTokenTypes.tCOLON ||
          builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer() // `:` or `,`
        }
      }

      if (isNewStyle) {
        if (builder.getTokenType != ScalaTokenTypes.tRBRACE) builder.error(ErrMsg("rbrace.expected"))
        builder.advanceLexer() // `}`
      }

      true
    }
  }
}

/**
 * ContextBound ::= Type [`as` id]
 */
object ContextBound {
  def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val contextBoundMarker = builder.mark()
    if (!Type()) {
      contextBoundMarker.rollbackTo()
      false
    } else {
      if (builder.features.`new context bounds and givens` &&
        builder.tryParseSoftKeyword(ScalaTokenType.AsKeyword)) {
        if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
          builder.advanceLexer() // context bound name
          contextBoundMarker.done(ScalaElementType.CONTEXT_BOUND)
          true
        } else {
          contextBoundMarker.error(ErrMsg("identifier.expected"))
          false
        }
      } else {
        contextBoundMarker.done(ScalaElementType.CONTEXT_BOUND)
        true
      }
    }
  }
}
