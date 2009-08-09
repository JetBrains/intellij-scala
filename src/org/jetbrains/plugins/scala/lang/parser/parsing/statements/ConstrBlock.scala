package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.lang.PsiBuilder
import expressions.{SelfInvocation, BlockStat}
import lexer.ScalaTokenTypes
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

object ConstrBlock {
  def parse(builder: PsiBuilder): Boolean = {
    val constrExprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        builder.advanceLexer //Ate {
        SelfInvocation parse builder
        while (true) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer //Ate }
              constrExprMarker.done(ScalaElementTypes.CONSTR_BLOCK)
              return true
            }
            case ScalaTokenTypes.tLINE_TERMINATOR
               | ScalaTokenTypes.tSEMICOLON => {
              builder.advanceLexer //Ate semi
              BlockStat parse builder
            }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              ParserUtils.roll(builder)
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