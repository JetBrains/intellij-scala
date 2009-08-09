package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.SimpleType

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

object Requires {
  def parse(builder: PsiBuilder): Boolean = {
    val requiresMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kREQUIRES => {
        builder.advanceLexer //Ate requires
        //parsing simple type
        if (!SimpleType.parse(builder)) {
          builder error ScalaBundle.message("simple.type.expected.requires")
        }
        requiresMarker.done(ScalaElementTypes.REQUIRES_BLOCK)
        return true
      }
      case _ => {
        requiresMarker.drop
        return false
      }
    }
  }
}