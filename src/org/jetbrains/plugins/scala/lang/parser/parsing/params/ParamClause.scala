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
 * ParamClause ::= [nl] '(' [Params] ')'
 */
object ParamClause extends ParamClause {
  override protected val params = Params
}

trait ParamClause {
  protected val params: Params

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark
    if (builder.twoNewlinesBeforeCurrentToken) {
      paramMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines
      case _ =>
        paramMarker.rollbackTo()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPLICIT =>
        paramMarker.rollbackTo()
        builder.restoreNewlinesState
        return false
      case _ =>
    }
    params parse builder
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
      case _ =>
        builder error ScalaBundle.message("rparenthesis.expected")
    }
    builder.restoreNewlinesState
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    true
  }
}