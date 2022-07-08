package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * ClassParamClause ::= [nl] '(' 'implicit' ClassParam {',' ClassParam} ')'
 */
object ImplicitClassParamClause extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark()
    if (builder.twoNewlinesBeforeCurrentToken) {
      classParamMarker.rollbackTo()
      return false
    }
    //Look for '('
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate '('
        builder.disableNewlines()
        //Look for implicit
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT =>
            //It's ok
            builder.advanceLexer() //Ate implicit
          case _ =>
            builder.error(ErrMsg("wrong.parameter"))
        }
        //ok, let's parse parameters
        if (!ClassParam()) {
          classParamMarker.rollbackTo()
          builder.restoreNewlinesState()
          return false
        }
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
          builder.advanceLexer() //Ate ,
          if (!ClassParam()) {
            builder.error(ErrMsg("wrong.parameter"))
          }
        }
      case _ =>
        classParamMarker.rollbackTo()
        return false
    }
    //Look for ')'
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
      case _ =>
        builder.error(ErrMsg("rparenthesis.expected"))
    }

    builder.restoreNewlinesState()
    classParamMarker.done(ScalaElementType.PARAM_CLAUSE)
    true
  }
}