package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.CompoundType

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ResultExpr ::= Expr1
 *              | (Bindings | id ':' CompoundType) '=>' Block
 */

object ResultExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val resultMarker = builder.mark
    val backupMarker = builder.mark

    def parseFunctionEnd() = builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer //Ate =>
        Block parse (builder, false)
        backupMarker.drop
        resultMarker.done(ScalaElementTypes.FUNCTION_EXPR)
        true
      }
      case _ => {
        resultMarker.drop
        backupMarker.rollbackTo
        false
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        Bindings parse builder
        return parseFunctionEnd
      }
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER=> {
        val pmarker = builder.mark
        builder.advanceLexer //Ate id
        if (ScalaTokenTypes.tCOLON == builder.getTokenType) {
          builder.advanceLexer // ate ':'
          val pt = builder.mark
          CompoundType.parse(builder)
          pt.done(ScalaElementTypes.PARAM_TYPE)
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            val psm = pmarker.precede // 'parameter clause'
            val pssm = psm.precede // 'parameter list'
            psm.done(ScalaElementTypes.PARAM_CLAUSE)
            pssm.done(ScalaElementTypes.PARAM_CLAUSES)
            pmarker.done(ScalaElementTypes.PARAM)
            
            return parseFunctionEnd
          }
          case _ => {
            builder error ErrMsg("fun.sign.expected")
          }
        }
        return parseFunctionEnd
      }
      case _ => {
        backupMarker.drop
      }
    }
    resultMarker.drop
    return false
  }
}