package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import base.Ids
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * ValDcl ::= ids ':' Type
 */

object ValDcl {
  def parse(builder: PsiBuilder): Boolean = {
    val returnMarker = builder.mark
    //Look for val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => builder.advanceLexer //Ate val
      case _ => {
        returnMarker.rollbackTo
        return false
      }
    }
    //Look for identifier
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        Ids parse builder
        //Look for :
        builder.getTokenType match {
          case ScalaTokenTypes.tCOLON => {
            builder.advanceLexer //Ate :
            if (Type.parse(builder)) {
              returnMarker.drop
              return true
            }
            else {
              builder error ErrMsg("wrong.type")
              returnMarker.drop
              return true
            }
          }
          case _ => {
            builder error ErrMsg("wrong.val.declaration")
            returnMarker.drop
            return true
          }
        }
      }
      case _ => {
        builder error ErrMsg("identifier.expected")
        returnMarker.drop
        return false
      }
    }
  }
}