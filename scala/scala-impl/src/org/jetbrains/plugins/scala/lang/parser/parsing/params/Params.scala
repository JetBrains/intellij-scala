package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

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
        builder error ScalaBundle.message("wrong.parameter")
      }
    }
    true
  }
}