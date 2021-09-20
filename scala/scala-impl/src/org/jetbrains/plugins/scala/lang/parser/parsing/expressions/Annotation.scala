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

  def apply(countLinesAfterAnnotation: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType != ScalaTokenTypes.tAT)
      return false

    val rollbackMarker = builder.mark()
    val annotMarker = builder.mark()

    builder.advanceLexer() //Ate @

    if (!AnnotationExpr()) {
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