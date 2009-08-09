package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  AccessModifier ::= private [ '[' (id | 'this') ']' ]
 *                   | protected [ '[' (id | 'this') ']' ]
 */

object AccessModifier {
  def parse(builder: PsiBuilder): Boolean = {
    val accessMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kPRIVATE |
           ScalaTokenTypes.kPROTECTED => builder.advanceLexer //Ate modifier
      case _ => {
        accessMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET => {
        builder.advanceLexer //Ate [
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER |
               ScalaTokenTypes.kTHIS => builder.advanceLexer //Ate identifier or this
          case _ => builder error ErrMsg("identifier.expected")
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tRSQBRACKET => builder.advanceLexer //Ate ]
          case _ => builder error ErrMsg("rsqbracket.expected")
        }
        accessMarker.done(ScalaElementTypes.ACCESS_MODIFIER)
        return true
      }
      case _ => {
        accessMarker.done(ScalaElementTypes.ACCESS_MODIFIER)
        return true
      }
    }
  }
}