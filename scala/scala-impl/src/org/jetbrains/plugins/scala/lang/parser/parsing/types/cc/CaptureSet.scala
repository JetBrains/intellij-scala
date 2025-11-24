package org.jetbrains.plugins.scala.lang.parser.parsing.types.cc

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

object CaptureSet extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      val marker = builder.mark()
      builder.advanceLexer()
      ParserUtils.parseLoopUntilRBrace(braceReported = true) {
        // parse anything except braces. Let parseLoopUntilRBrace handle them
        while ({
          val ty = builder.getTokenType
          ty != ScalaTokenTypes.tLBRACE && ty != ScalaTokenTypes.tRBRACE
        }) {
          builder.advanceLexer()
        }
      }
      marker.done(ScalaElementType.CAPTURE_SET)
      true
    } else {
      false
    }
  }
}
