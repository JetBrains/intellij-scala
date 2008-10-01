package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * Expr ::= (Bindings | id) '=>' Expr
 *        | Expr1
 */

object Expr {
  def parse(builder: PsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val pmarker = builder.mark
        builder.advanceLexer //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            val psm = pmarker.precede // 'parameter clause'
            val pssm = psm.precede // 'parameter list'
            psm.done(ScalaElementTypes.PARAM_CLAUSE)
            pssm.done(ScalaElementTypes.PARAM_CLAUSES)
            pmarker.done(ScalaElementTypes.PARAM)

            builder.advanceLexer //Ate =>
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
            return true
          }
          case _ => {
            pmarker.drop
            exprMarker.rollbackTo
          }
        }
      }
      case ScalaTokenTypes.tLPARENTHESIS => {
        if (Bindings.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              builder.advanceLexer //Ate =>
              if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
              exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
              return true
            }
            case _ => exprMarker.rollbackTo
          }
        }
        else {
          exprMarker.drop
        }
      }
      case _ => exprMarker.drop
    }
    return Expr1.parse(builder)
  }
}