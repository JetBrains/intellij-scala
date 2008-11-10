package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ParamClause ::= [nl] '(' [Params] ')'
 */

object ParamClause {
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
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    return true
  }
}