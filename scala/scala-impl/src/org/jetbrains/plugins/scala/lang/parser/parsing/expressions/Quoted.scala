package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

object Quoted {
  def parse(builder: ScalaPsiBuilder): Unit = {
    assert(builder.getTokenType == ScalaTokenType.QuoteStart)
    val marker = builder.mark()
    builder.advanceLexer()
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer()
        Block.parse(builder)
        if (builder.getTokenType == ScalaTokenTypes.tRBRACE) {
          builder.advanceLexer()
        } else {
          builder error ErrMsg("rbrace.expected")
        }
        marker.done(ScalaElementType.QUOTED_BLOCK)
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer()
        if (!Type.parse(builder)) {
          builder error ErrMsg("type.expected")
          marker.drop()
          return
        }
        if (builder.getTokenType != ScalaTokenTypes.tRSQBRACKET) {
          builder error ErrMsg("rsqbracket.expected")
          marker.drop()
          return
        }

        builder.advanceLexer()
        marker.done(ScalaElementType.QUOTED_TYPE)
    }
  }
}
