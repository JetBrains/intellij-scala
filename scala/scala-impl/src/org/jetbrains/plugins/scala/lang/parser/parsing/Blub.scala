package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object Blub extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val m = builder.mark()
    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
      builder.advanceLexer()
      builder.advanceLexer()
    } else {
      builder.error(ScalaBundle.message("identifier.expected"))
    }
    m.done(ScalaElementType.BLOCK)
    true
  }
}
