package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.lang.PsiBuilder
import expressions.{SelfInvocation, BlockStat}
import lexer.ScalaTokenTypes
import util.ParserUtils
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

object ConstrBlock {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
        builder.enableNewlines
        SelfInvocation parse builder
        while (true) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer //Ate }
              builder.restoreNewlinesState
              constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
              return true
            }
            case ScalaTokenTypes.tSEMICOLON => {
              builder.advanceLexer //Ate semi
              BlockStat parse builder
            }
            case _ if builder.newlineBeforeCurrentToken =>
              if (!BlockStat.parse(builder)) {
                builder error ErrMsg("rbrace.expected")
                builder.restoreNewlinesState
                while (!builder.eof && !ScalaTokenTypes.tRBRACE.eq(builder.getTokenType) &&
                  !builder.newlineBeforeCurrentToken) {
                  builder.advanceLexer
                }
                constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
                return true
              }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              builder.restoreNewlinesState
              while (!builder.eof && !ScalaTokenTypes.tRBRACE.eq(builder.getTokenType) &&
                !builder.newlineBeforeCurrentToken) {
                builder.advanceLexer
              }
              constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
              return true
            }
          }
        }
        return true //it's trick to compiler
      }
      case _ => {
        constrExprMarker.drop
        return false
      }
    }
  }
}