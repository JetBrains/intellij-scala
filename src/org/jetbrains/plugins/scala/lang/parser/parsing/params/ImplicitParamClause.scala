package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ImplicitParamClause ::= [nl] '(' 'implicit' Params ')'
 */

object ImplicitParamClause {
  def parse(builder: PsiBuilder): Boolean = {
    val paramMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) {
          builder.advanceLexer //Ate nl
        }
        else {
          paramMarker.drop
          return false
        }
      }
      case _ => {}
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPLICIT => {
        builder.advanceLexer //Ate implicit
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    if (!Params.parse(builder)) {
      builder error ScalaBundle.message("implicit.params.excepted")
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
      }
      case _ => {
        builder error ScalaBundle.message("rparenthesis.expected")
      }
    }
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    return true
  }
}