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
 *  ids ::= id { ,  id}
 */

object Ids {
  def parse(builder: PsiBuilder): Boolean = {
    val idListMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val m = builder.mark
        builder.advanceLexer //Ate identifier
        m.done(ScalaElementTypes.FIELD_ID)
      }
      case _ => {
        idListMarker.drop
        return false
      }
    }
    while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer //Ate ,
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          val m = builder.mark
          builder.advanceLexer //Ate identifier
          m.done(ScalaElementTypes.FIELD_ID)
        }
        case _ => {
          builder error ErrMsg("identifier.expected")
          idListMarker.done(ScalaElementTypes.IDENTIFIER_LIST)
          return true
        }
      }
    }
    idListMarker.done(ScalaElementTypes.IDENTIFIER_LIST)
    return true
  }
}