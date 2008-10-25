package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import types.InfixType

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

object Ascription {
  def parse(builder: PsiBuilder): Boolean = {
    val ascriptionMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON => {
        builder.advanceLexer //Ate :
      }
      case _ => {
        ascriptionMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => {
        val seqArgMarker = builder.mark
        ascriptionMarker.drop
        builder.advanceLexer //Ate _
        builder.getTokenText match {
          case "*" => {
            builder.advanceLexer //Ate *
          }
          case _ => {
            builder error ScalaBundle.message("star.expected", new Array[Object](0))
          }
        }
        seqArgMarker.done(ScalaElementTypes.SEQUENCE_ARG)
        return true
      }
      case _ => {}
    }
    if (!InfixType.parse(builder)) {
      var x = 0;
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {
        x = x + 1
      }
      annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
      if (x == 0) builder error ScalaBundle.message("annotation.expected", new Array[Object](0))
    }
    ascriptionMarker.drop
    return true
  }
}