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

  def parse(
    builder:              ScalaPsiBuilder,
    mayHaveVariance:      Boolean = true,
    mayHaveViewBounds:    Boolean = true,
    mayHaveContextBounds: Boolean = true
  ): Boolean = {
    val typeMarker = builder.mark
    val parseTypeParam = () => TypeParam.parse(builder, mayHaveVariance, mayHaveViewBounds, mayHaveContextBounds)

    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer() //Ate [
        builder.disableNewlines()
      case _ =>
        typeMarker.drop()
        return false
    }

    if (!parseTypeParam()) {
      builder.error(ScalaBundle.message("wrong.parameter"))
    }

    while (builder.getTokenType == ScalaTokenTypes.tCOMMA &&
           !builder.consumeTrailingComma(ScalaTokenTypes.tRSQBRACKET)) {
      builder.advanceLexer() //Ate
      if (!parseTypeParam()) {
        builder.error(ScalaBundle.message("wrong.parameter"))
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