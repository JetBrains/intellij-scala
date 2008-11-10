package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ArgumentExprs ::= '(' [Exprs [',']] ')'
 *                 | [nl] BlockExpr
 */

object ArgumentExprs {
  def parse(builder: PsiBuilder): Boolean = {
    val argMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
        Expr parse builder
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer
          if (!Expr.parse(builder)) {
            builder error ErrMsg("wrong.expression")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
          }
          case _ => {
            builder error ScalaBundle.message("rparenthesis.expected")
          }
        }
        argMarker.done(ScalaElementTypes.ARG_EXPRS)
        return true
      }
      case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tLBRACE => {
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (!LineTerminator(builder.getTokenText)) {
              argMarker.rollbackTo
              return false
            }
            else {
              builder.advanceLexer //Ate nl
            }
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            BlockExpr parse builder
            argMarker.done(ScalaElementTypes.ARG_EXPRS)
            return true
          }
          case _ => {
            argMarker.rollbackTo
            return false
          }
        }
      }
      case _ => {
        argMarker.drop
        return false
      }
    }
  }
}