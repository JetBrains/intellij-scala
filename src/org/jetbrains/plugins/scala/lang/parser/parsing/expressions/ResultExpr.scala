package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.CompoundType
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ResultExpr ::= Expr1
 *              | (Bindings | id ':' CompoundType) '=>' Block
 */

object ResultExpr {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val resultMarker = builder.mark
    val backupMarker = builder.mark

    def parseFunctionEnd() = builder.getTokenType match {
      case ScalaTokenTypes.tFUNTYPE => {
        builder.advanceLexer() //Ate =>
        Block parse (builder, false)
        backupMarker.drop()
        resultMarker.done(ScalaElementTypes.FUNCTION_EXPR)
        true
      }
      case _ => {
        resultMarker.drop()
        backupMarker.rollbackTo()
        false
      }
    }

    def parseFunction(paramsMarker: PsiBuilder.Marker): Boolean = {
      val paramMarker = builder.mark()
      builder.advanceLexer() //Ate id
      if (ScalaTokenTypes.tCOLON == builder.getTokenType) {
        builder.advanceLexer() // ate ':'
        val pt = builder.mark
        CompoundType.parse(builder)
        pt.done(ScalaElementTypes.PARAM_TYPE)
      }
      builder.getTokenType match {
        case ScalaTokenTypes.tFUNTYPE => {
          val psm = paramsMarker.precede // 'parameter list'
          paramMarker.done(ScalaElementTypes.PARAM)
          paramsMarker.done(ScalaElementTypes.PARAM_CLAUSE)
          psm.done(ScalaElementTypes.PARAM_CLAUSES)

          return parseFunctionEnd()
        }
        case _ => {
          builder error ErrMsg("fun.sign.expected")
        }
      }
      parseFunctionEnd()
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        Bindings parse builder
        return parseFunctionEnd()
      }
      case ScalaTokenTypes.kIMPLICIT =>
        val pmarker = builder.mark()
        builder.advanceLexer() //ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            return parseFunction(pmarker)
          case _ =>
            resultMarker.drop()
            backupMarker.rollbackTo()
            return false
        }
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        val pmarker = builder.mark
        return parseFunction(pmarker)
      }
      case _ => {
        backupMarker.drop()
      }
    }
    resultMarker.drop()
    false
  }
}