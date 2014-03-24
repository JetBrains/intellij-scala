package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

object NameValuePair {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val nameMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        builder.advanceLexer //Ate val
      }
      case _ => {
        nameMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        builder.advanceLexer //Ate id
      }
      case _ => {
        builder error ScalaBundle.message("identifier.expected")
        nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
        return true
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tASSIGN => {
        builder.advanceLexer //Ate =
      }
      case _ => {
        builder error ScalaBundle.message("assign.expected")
      }
    }
    if (!PrefixExpr.parse(builder, isPattern = false)) {
      builder error ScalaBundle.message("wrong.expression")
    }
    nameMarker.done(ScalaElementTypes.NAME_VALUE_PAIR)
    return true
  }
}