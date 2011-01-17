package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.Type
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 * FunDcl ::= FunSig [':' Type]
 */

object FunDcl {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    //val returnMarker = builder.mark
    builder.getTokenType() match {
      case ScalaTokenTypes.kDEF => {
        builder.advanceLexer //Ate def
      }
      case _ => {
        //returnMarker.drop
        return false
      }
    }
    if (!(FunSig parse builder)) {
      //returnMarker.drop
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
        if (Type.parse(builder)) {
          //returnMarker.drop
          return true
        }
        else {
          builder error ScalaBundle.message("wrong.type")
          //returnMarker.drop
          return true
        }
      }
      case _ => {
        //returnMarker.drop
        return true
      }
    }
  }
}