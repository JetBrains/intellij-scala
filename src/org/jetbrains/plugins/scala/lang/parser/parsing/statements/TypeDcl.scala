package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import params.TypeParamClause
import types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * TypeDcl ::= id [TypeParamClause] ['>;' Type] ['<:' Type]
 */

object TypeDcl {
  def parse(builder: PsiBuilder): Boolean = {
    val returnMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        builder.advanceLexer //Ate def
      }
      case _ => {
        returnMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        builder.advanceLexer //Ate nl
      }
      case _ => {}//nothing
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate identifier
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected")
        returnMarker.drop
        return false
      }
    }
    val isTypeParamClause = if (TypeParamClause parse builder) {
      true
    } else false
    builder.getTokenText match {
      case ">:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type")
        }
      }
      case _ => {} //nothing
    }
    builder.getTokenText match {
      case "<:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type")
        }
      }
      case _ => {} //nothing
    }
    returnMarker.drop
    return true
  }
}