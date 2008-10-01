package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import bnf.BNF
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
        builder error ScalaBundle.message("identifier.expected", new Array[Object](0))
        /*returnMarker.drop
        return true*/
        returnMarker.rollbackTo
        return false
      }
    }
    var isTypeParamClause = false;
    if (BNF.firstTypeParamClause.contains(builder.getTokenType)) {
      isTypeParamClause = TypeParamClause parse builder
    }
    builder.getTokenText match {
      case ">:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
      }
      case _ => {} //nothing
    }
    builder.getTokenText match {
      case "<:" => {
        builder.advanceLexer
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type", new Array[Object](0))
        }
      }
      case _ => {} //nothing
    }
    returnMarker.drop
    return true
  }
}