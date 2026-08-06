package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

object Quoted extends ParsingRule {
  final override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.getTokenType == ScalaTokenType.QuoteStart)
    val marker = builder.mark()
    builder.advanceLexer()
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer()
        ParserUtils.parseLoopUntilRBrace() {
          Block.ContentInBraces()
        }
        marker.done(ScalaElementType.QUOTED_BLOCK)
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer()
        if (!Type()) {
          builder error ErrMsg("type.expected")
          marker.drop()
        } else if (builder.getTokenType != ScalaTokenTypes.tRSQBRACKET) {
          builder error ErrMsg("rsqbracket.expected")
          marker.drop()
        } else {
          builder.advanceLexer()
          marker.done(ScalaElementType.QUOTED_TYPE)
        }
    }
    true
  }
}
