package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClause ::= [nl] '(' 'implicit' ClassParam {',' ClassParam} ')'
 */

object ImplicitClassParamClause {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParamMarker = builder.mark
    if (builder.countNewlineBeforeCurrentToken > 1) {
      classParamMarker.rollbackTo
      return false
    }
    //Look for '('
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate '('
        builder.disableNewlines
        //Look for implicit
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT => {
            //It's ok
            builder.advanceLexer //Ate implicit
          }
          case _ => {
            builder error ErrMsg("wrong.parameter")
          }
        }
        //ok, let's parse parameters
        if (!(ClassParam parse builder)) {
          classParamMarker.rollbackTo
          builder.restoreNewlinesState
          return false
        }
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer //Ate ,
          if (!(ClassParam parse builder)) {
            classParamMarker.rollbackTo
            builder.restoreNewlinesState
            return false
          }
        }
      }
      case _ => {
        classParamMarker.rollbackTo
        return false
      }
    }
    //Look for ')'
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
        builder.restoreNewlinesState
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        return true
      }
      case _ => {
        builder.restoreNewlinesState
        classParamMarker.rollbackTo
        return false
      }
    }
  }
}