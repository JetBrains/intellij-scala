package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
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
object Ascription {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val ascriptionMarker = builder.mark

    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
      case _ =>
        ascriptionMarker.drop()
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        val seqArgMarker = builder.mark
        builder.advanceLexer() //Ate _
        builder.getTokenText match {
          case "*" =>
            ascriptionMarker.drop()
            builder.advanceLexer() //Ate *
            seqArgMarker.done(ScalaElementType.SEQUENCE_ARG)
            return true
          case _ =>
            seqArgMarker.rollbackTo()
        }
      case _ =>
    }

    if (!Type.parse(builder)) {
      var x = 0
      val annotationsMarker = builder.mark
      while (Annotation.parse(builder)) {
        x = x + 1
      }
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      if (x == 0) {
        builder.error(ScalaBundle.message("annotation.or.type.expected"))
      }
    }

    ascriptionMarker.drop()
    true
  }
}