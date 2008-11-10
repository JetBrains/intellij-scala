package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Annmotation ::= '@' AnnotationExpr [nl]
 */

object Annotation {
  def parse(builder: PsiBuilder): Boolean = {
    val annotMarker = builder.mark
    builder.getTokenText match {
      case "@" => {
        builder.advanceLexer //Ate @
      }
      case _ => {
        annotMarker.drop
        return false
      }
    }
    if (!AnnotationExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.annotation.expression")
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        if (LineTerminator(builder.getTokenText)) builder.advanceLexer
      }
      case _ => {}
    }
    annotMarker.done(ScalaElementTypes.ANNOTATION)
    return true
  }
}