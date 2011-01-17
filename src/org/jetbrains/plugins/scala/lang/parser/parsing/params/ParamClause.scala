package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ParamClause ::= [nl] '(' [Params] ')'
 */

object ParamClause {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val paramMarker = builder.mark
    if (builder.countNewlineBeforeCurrentToken > 1) {
      paramMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
        builder.disableNewlines
      }
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPLICIT => {
        paramMarker.rollbackTo
        return false
      }
      case _ => {}
    }
    Params parse builder
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
      }
      case _ => {
        builder error ScalaBundle.message("rparenthesis.expected")
      }
    }
    builder.restoreNewlinesState
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    return true
  }
}