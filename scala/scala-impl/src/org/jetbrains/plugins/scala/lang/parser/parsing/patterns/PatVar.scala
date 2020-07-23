package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object PatVar extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER if builder.isIdBinding =>
        builder.advanceLexer() //Ate id
        true

      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        true
      case _ => false
    }
  }
}
