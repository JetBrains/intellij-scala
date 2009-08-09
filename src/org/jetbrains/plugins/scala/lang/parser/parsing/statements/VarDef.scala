package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import base.Ids
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.Type
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 *  ValDef ::= PatDef |
 *             ids ':' Type '=' '_'
 */
object VarDef {
  def parse(builder: PsiBuilder): Boolean = {
    if (PatDef parse builder) {
      return true
    }

    // Parsing specifig wildcard definition
    val valDefMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        Ids parse builder
        var hasTypeDcl = false

        if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
          if (!Type.parse(builder)) {
            builder error "type declaration expected"
          }
          hasTypeDcl = true
        }
        else {
          valDefMarker.rollbackTo
          return false
        }
        if (!ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
          valDefMarker.rollbackTo
          return false
        } else {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)
          builder.getTokenType match {
            case ScalaTokenTypes.tUNDER => builder.advanceLexer
            //Ate _
            case _ => {
              valDefMarker.rollbackTo
              return false
            }
          }
          valDefMarker.drop
          return true
        }
      }
      case _ => {
        valDefMarker.drop
        return false
      }
    }
  }
}