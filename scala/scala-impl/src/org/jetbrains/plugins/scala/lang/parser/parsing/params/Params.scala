package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ScalaParserBundle, ScalaPsiBuilderExt}

/*
 * Params ::= Param {',' Param}
 */
object Params extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!Param()) {
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      builder.advanceLexer() //Ate ,
      if (!Param()) {
        builder.error(ScalaParserBundle.message("wrong.parameter"))
      }
    }
    true
  }
}
