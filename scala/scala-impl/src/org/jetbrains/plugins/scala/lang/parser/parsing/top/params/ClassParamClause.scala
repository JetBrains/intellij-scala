package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypesAsClassParams

/*
 * ClassParamClause ::= [nl] '(' [ClassParam {',' ClassParam}] ')'
 */
object ClassParamClause extends ParsingRule {

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
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT =>
            classParamMarker.rollbackTo()
            builder.restoreNewlinesState()
            return false
          case _ =>
        }

        def parseNormalClassParams(): Unit = {
          //ok, let's parse parameters
          if (ClassParam()) {
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !builder.consumeTrailingComma(ScalaTokenTypes.tRPARENTHESIS)) {
              builder.advanceLexer() //Ate ,
              if (!(ClassParam())) {
                builder error ErrMsg("wrong.parameter")
              }
            }
          }
        }

        if (builder.isScala3 && builder.tryParseSoftKeyword(ScalaTokenType.UsingKeyword)) {
          if (!TypesAsClassParams()) {
            parseNormalClassParams()
          }
        } else {
          parseNormalClassParams()
        }
      case _ =>
        classParamMarker.rollbackTo()
        return false
    }
    //Look for ')'
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS =>
        builder.advanceLexer() //Ate )
        builder.restoreNewlinesState()
        classParamMarker.done(ScalaElementType.PARAM_CLAUSE)
        true
      case _ =>
        classParamMarker.done(ScalaElementType.PARAM_CLAUSE)
        builder error ErrMsg("rparenthesis.expected")
        builder.restoreNewlinesState()
        true
    }
  }
}