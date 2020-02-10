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
 * TypeParamClause ::= '[' VariantTypeParam {',' VariantTypeParam} ']'
 */
object TypeParamClause {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val typeMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer() //Ate [
        builder.disableNewlines()
      case _ =>
        typeMarker.drop()
        return false
    }
    if (!TypeParam.parse(builder)) {
      builder error ScalaBundle.message("wrong.parameter")
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRSQBRACKET)) {
      builder.advanceLexer() //Ate
      if (!TypeParam.parse(builder)) {
        builder error ErrMsg("wrong.parameter")
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRSQBRACKET =>
        builder.advanceLexer() //Ate ]
      case _ =>
        builder error ScalaBundle.message("rsqbracket.expected")
    }
    builder.restoreNewlinesState()
    typeMarker.done(ScalaElementType.TYPE_PARAM_CLAUSE)
    true
  }
}