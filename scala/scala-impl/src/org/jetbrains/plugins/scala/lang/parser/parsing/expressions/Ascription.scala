package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

/*
 * Scala2:
 * Ascription ::= ':' InfixType
 *              | ':' Annotation {Annotation}
 *              | ':' '_' '*
 * Scala3:
 * Ascription ::=  ‘:’ InfixType
                |  ‘:’ Annotation {Annotation}
 */
object Ascription extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
      case _ =>
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        val seqArgMarker = builder.mark()
        builder.advanceLexer() //Ate _
        builder.getTokenText match {
          case "*" =>
            builder.advanceLexer() //Ate *
            seqArgMarker.done(ScalaElementType.SEQUENCE_ARG)
            return true
          case _ =>
            seqArgMarker.rollbackTo()
        }
      case _ =>
    }

    if (!Type()) {
      val annotationsMarker = builder.mark()
      var x = 0
      while (Annotation(countLinesAfterAnnotation = x > 0)) {
        x = x + 1
      }
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      if (x == 0) {
        builder.error(ScalaBundle.message("annotation.or.type.expected"))
      }
    }

    true
  }
}