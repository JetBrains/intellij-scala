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
 * FunTypeParam ::= '[' TypeParam {',' TypeParam} ']'
 */

object FunTypeParamClause {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val funMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        builder.advanceLexer //Ate [
        builder.disableNewlines
      case _ => {
        funMarker.drop
        return false
      }
    }
    if (!TypeParam.parse(builder, false)) {
      builder error ErrMsg("wrong.parameter")
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate
      if (!TypeParam.parse(builder, false)) {
        builder error ErrMsg("wrong.parameter")
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRSQBRACKET => {
        builder.advanceLexer //Ate ]
      }
      case _ => builder error ErrMsg("wrong.parameter")
    }
    builder.restoreNewlinesState
    funMarker.done(ScalaElementTypes.TYPE_PARAM_CLAUSE)
    return true
  }
}