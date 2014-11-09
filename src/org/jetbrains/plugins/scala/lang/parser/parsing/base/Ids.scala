package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 *  ids ::= id { ,  id}
 */

object Ids {
  def parse(builder: ScalaPsiBuilder): Boolean = {
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