package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.Type

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Binding ::= id [: Type]
 */

object Binding {
  def parse(builder: PsiBuilder): Boolean = {
    val bindingMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate id
      }
      case _ => {
        bindingMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
        if (!Type.parse(builder)) {
          builder error ScalaBundle.message("wrong.type")
        }
      }
      case _ => {}
    }
    bindingMarker.done(ScalaElementTypes.PARAM)
    return true
  }
}