package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *         Date: 06.03.2008
 */

/*
 * Annmotation ::= '@' AnnotationExpr [nl]
 */
object Annotation {

  def parse(builder: ScalaPsiBuilder, countLinesAfterAnnotation: Boolean = true): Boolean = {
    val rollbackMarker = builder.mark()
    val annotMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tAT => 
        builder.advanceLexer() //Ate @
      case _ =>
        annotMarker.drop()
        rollbackMarker.drop()
        return false
    }
    if (!AnnotationExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.annotation.expression")
      annotMarker.drop()
    } else {
      annotMarker.done(ScalaElementType.ANNOTATION)
    }
    if (countLinesAfterAnnotation && builder.twoNewlinesBeforeCurrentToken) {
      rollbackMarker.rollbackTo()
      return false
    } else rollbackMarker.drop()
    true
  }
}