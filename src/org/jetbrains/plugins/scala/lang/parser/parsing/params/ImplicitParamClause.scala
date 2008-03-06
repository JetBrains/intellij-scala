package org.jetbrains.plugins.scala.lang.parser.parsing.params

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 17:34:11
* To change this template use File | Settings | File Templates.
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
      builder error ScalaBundle.message("implicit.params.excepted", new Array[Object](0))
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
      }
      case _ => {
        builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
      }
    }
    paramMarker.done(ScalaElementTypes.PARAM_CLAUSE)
    return true
  }
}