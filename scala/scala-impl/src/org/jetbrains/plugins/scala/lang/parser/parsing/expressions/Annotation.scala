package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

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

  def skipUnattachedAnnotations(@Nls missingTargetMessage: => String)(implicit builder: ScalaPsiBuilder): Boolean = {
    @tailrec
    def parseAtLeastOneAnnotation(hadAnnotation: Boolean = false): Boolean =
      if (Annotation()) parseAtLeastOneAnnotation(hadAnnotation = true) else hadAnnotation

    val parsedOneAnnotation = parseAtLeastOneAnnotation()
    if (parsedOneAnnotation) {
      builder error missingTargetMessage
    }
    parsedOneAnnotation
  }
}