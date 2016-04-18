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
 * ImplicitParamClause ::= [nl] '(' 'implicit' Params ')'
 */
object ImplicitParamClause extends ImplicitParamClause {
  override protected val params = Params
}

trait ImplicitParamClause {
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
        builder.advanceLexer() //Ate implicit
      case _ =>
        paramMarker.rollbackTo()
        builder.restoreNewlinesState
        return false
    }
    if (!params.parse(builder)) {
      builder error ScalaBundle.message("implicit.params.excepted")
    }
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