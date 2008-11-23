package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import params.TypeParamClause
import types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

object TypeDef {
  def parse(builder: PsiBuilder): Boolean = {
    val faultMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTYPE => {
        builder.advanceLexer //Ate type
      }
      case _ => {
        faultMarker.rollbackTo
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
        faultMarker.rollbackTo
        return false
      }
    }
    val isTypeParamClause = if (TypeParamClause parse builder) {
      true
    } else false
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN => {
        builder.advanceLexer //Ate =
        if (Type.parse(builder)) {
          faultMarker.drop
          return true
        }
        else {
          faultMarker.drop
          builder error ScalaBundle.message("wrong.type")
          return false
        }
      }
      case _ => {
        faultMarker.rollbackTo
        return false
      }
    }
  }
}