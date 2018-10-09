package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Params ::= Param {',' Param}
 */
object Params {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    if (!Param.parse(builder)) {
      return false
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
      builder.advanceLexer() //Ate ,
      if (!Param.parse(builder)) {
        builder error ScalaBundle.message("wrong.parameter")
      }
    }
    true
  }
}