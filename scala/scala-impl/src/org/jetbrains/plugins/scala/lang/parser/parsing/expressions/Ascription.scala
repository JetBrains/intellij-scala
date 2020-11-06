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
object Ascription extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tCOLON =>
        builder.advanceLexer() //Ate :
      case _ =>
        return false
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        val seqArgMarker = builder.mark
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

    if (!Type.parse(builder)) {
      val annotationsMarker = builder.mark
      var x = 0
      while (Annotation.parse(builder)) {
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