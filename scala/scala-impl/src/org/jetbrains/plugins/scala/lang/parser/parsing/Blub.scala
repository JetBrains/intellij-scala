package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object Blub extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    while (builder.getTokenType == ScalaTokenTypes.tRBRACE) {
      val m = builder.mark()
      builder.advanceLexer()
      if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
        builder.advanceLexer()
        m.rollbackTo()
        builder.advanceLexer()
      } else if (builder.getTokenType == ScalaTokenTypes.kIF) {
        builder.advanceLexer()
        m.drop()
      } else {
        builder.error(ScalaBundle.message("identifier.expected"))
        m.done(ScalaElementType.BLOCK)
      }
    }
    true
  }
}
