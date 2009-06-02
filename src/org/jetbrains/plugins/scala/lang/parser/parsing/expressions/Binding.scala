package org.jetbrains.plugins.scala.lang.parser.parsing.expressions


import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.ParamType

/**
 * @author Aleksander Podkhalyuzin
 * @date 05.04.2009
 */

/**
 * Binding ::= (id | '_') [':' Type]
 */

object Binding {
  def parse(builder: PsiBuilder): Boolean = {
    val paramMarker = builder.mark
    builder.mark.done(ScalaElementTypes.ANNOTATIONS)
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => builder.advanceLexer
      case _ => {
        paramMarker.rollbackTo
        return false
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
        if (!ParamType.parse(builder)) builder error ErrMsg("wrong.type")
      }
      case _ =>
    }

    paramMarker.done(ScalaElementTypes.PARAM)
    return true
  }
}