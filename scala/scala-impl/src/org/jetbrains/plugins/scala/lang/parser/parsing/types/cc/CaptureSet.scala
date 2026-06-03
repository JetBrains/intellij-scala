package org.jetbrains.plugins.scala.lang.parser.parsing.types.cc

import org.jetbrains.plugins.scala.ScalaBundle
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

      if (builder.getTokenType == ScalaTokenTypes.tRBRACE) {
        builder.advanceLexer()
      } else {
        ParserUtils.parseLoopUntilRBrace(braceReported = true) {
          while({
            CaptureRef()
            val nextToken = builder.getTokenType
            if (nextToken == ScalaTokenTypes.tCOMMA) {
              builder.advanceLexer()
              true
            } else {
              if (nextToken != ScalaTokenTypes.tRBRACE) {
                builder.error(ScalaBundle.message("comma.or.rbrace.expected"))

                if (nextToken == null || nextToken == ScalaTokenTypes.tLBRACE) {
                  // if we find a '{' let parseLoopUntilRBrace handle it
                  false
                } else {
                  builder.advanceLexer()
                  true
                }
              } else {
                false
              }
            }
          }) ()
        }
      }
      marker.done(ScalaElementType.CAPTURE_SET)
      true
    } else {
      false
    }
  }
}
