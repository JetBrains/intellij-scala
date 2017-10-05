package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClause ::= [nl] '(' [ClassParam {',' ClassParam}] ')'
 */
object ClassParamClause extends ClassParamClause {
  override protected def classParam = ClassParam
}

trait ClassParamClause {
  protected def classParam: ClassParam

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark
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
        //ok, let's parse parameters
        if (classParam parse builder) {
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA && !ParserUtils.eatTrailingComma(builder, ScalaTokenTypes.tRPARENTHESIS)) {
            builder.advanceLexer() //Ate ,
            if (!(classParam parse builder)) {
              builder error ErrMsg("wrong.parameter")
            }
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
        builder.restoreNewlinesState()
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        true
      case _ =>
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        builder error ErrMsg("rparenthesis.expected")
        builder.restoreNewlinesState()
        true
    }
  }
}