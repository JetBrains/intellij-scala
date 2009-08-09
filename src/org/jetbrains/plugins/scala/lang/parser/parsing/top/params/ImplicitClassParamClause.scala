package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 * ClassParamClause ::= [nl] '(' 'implicit' ClassParam {',' ClassParam} ')'
 */

object ImplicitClassParamClause {
  def parse(builder: PsiBuilder): Boolean = {
    val classParamMarker = builder.mark
    //try to miss nl token
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        //if we find more than one nl => false
        if (!LineTerminator(builder.getTokenText)) {
          classParamMarker.rollbackTo
          return false
        }
        else {
          builder.advanceLexer // Ate nl token
        }
      }
      case _ => {/*so let's parse*/}
    }
    //Look for '('
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate '('
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
          return false
        }
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer //Ate ,
          if (!(ClassParam parse builder)) {
            classParamMarker.rollbackTo
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
        classParamMarker.done(ScalaElementTypes.PARAM_CLAUSE)
        return true
      }
      case _ => {
        classParamMarker.rollbackTo
        return false
      }
    }
  }
}