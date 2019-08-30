package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 *
 * [[FunArgTypes]] ::= [[InfixType]]
 *                   | ‘(’ [[TypedFunParam]] {‘,’ [[TypedFunParam]] } ‘)’
 */
object FunArgTypes extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (InfixType.parse(builder)) {
      ???
    } else if (!TypedFunParam.parse()) {
      marker.rollbackTo()
      false
    } else {
      var exit = false
      while (!exit &&
             builder.getTokenType == ScalaTokenTypes.tCOMMA &&
             !builder.consumeTrailingComma(ScalaTokenTypes.tLPARENTHESIS)) {
        builder.advanceLexer()
        if (!TypedFunParam.parse()) exit = true
      }

      marker.done(ScalaElementType.PARAM_CLAUSE)
      marker.precede.done(ScalaElementType.PARAM_CLAUSES)
      true
    }
  }
}
