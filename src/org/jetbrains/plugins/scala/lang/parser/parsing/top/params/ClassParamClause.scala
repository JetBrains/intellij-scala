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
 * ClassParamClause ::= [nl] '(' [ClassParam {',' ClassParam}] ')'
 */

object ClassParamClause {
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
        builder.getTokenType match {
          case ScalaTokenTypes.kIMPLICIT => {
            classParamMarker.rollbackTo
            return false
          }
          case _ => {}
        }
        //ok, let's parse parameters
        if (ClassParam parse builder) {
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
            builder.advanceLexer //Ate ,
            if (!(ClassParam parse builder)) {
              builder error ErrMsg("wrong.parameter")
            }
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
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        builder error ErrMsg("rparenthesis.expected")
        builder.restoreNewlinesState
        return true
      }
    }
  }
}