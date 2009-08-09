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
 * VarDcl ::= ids ':' Type
 */

object VarDcl {
  def parse(builder: PsiBuilder): Boolean = {
    val returnMarker = builder.mark
    //Look for val
    builder.getTokenType match {
      case ScalaTokenTypes.kVAR => builder.advanceLexer //Ate var
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
              builder error ScalaBundle.message("wrong.type")
              returnMarker.drop
              return true
            }
          }
          case _ => {
            builder error ScalaBundle.message("wrong.var.declaration")
            returnMarker.drop
            return true
          }
        }
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected")
        returnMarker.drop
        return false
      }
    }
  }
}