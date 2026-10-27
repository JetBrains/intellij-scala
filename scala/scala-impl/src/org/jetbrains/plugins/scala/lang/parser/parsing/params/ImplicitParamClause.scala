package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.{ScalaElementType, ScalaParserBundle}

/*
 * ImplicitParamClause ::= [nl] '(' 'implicit' Params ')'
 */
object ImplicitParamClause extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark()
    if (builder.twoNewlinesBeforeCurrentToken) {
      paramMarker.drop()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
      case _ =>
        paramMarker.rollbackTo()
        return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPLICIT =>
        builder.advanceLexer() //Ate implicit
      case _ =>
        paramMarker.rollbackTo()
        builder.restoreNewlinesState()
        return false
    }
    if (!Params()) {
      builder.error(ScalaParserBundle.message("implicit.params.excepted"))
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
      case _ =>
        builder.error(ScalaParserBundle.message("rparenthesis.expected"))
    }
    builder.restoreNewlinesState()
    paramMarker.done(ScalaElementType.PARAM_CLAUSE)
    true
  }
}
