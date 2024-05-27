package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object Patterns extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    def parsePattern(): Boolean = {
      if (builder.features.`named tuples` && builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tASSIGN)) {
        // Parse named tuple pattern, but only consume tokens for now...
        // Later we want to have special psi elements ala ScNamedTupleElement
        builder.advanceLexer()
        builder.advanceLexer()
      }
      Pattern()
    }

    val patternsMarker = builder.mark()
    if (!parsePattern()) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER =>
          builder.advanceLexer()
          builder.getTokenText match {
            case "*" =>
              builder.advanceLexer()
              patternsMarker.done(ScalaElementType.SEQ_WILDCARD_PATTERN)
              return true
            case _ =>
          }
        case _ =>
      }
      patternsMarker.rollbackTo()
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA =>
        builder.advanceLexer() //Ate ,
        while (parsePattern()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA =>
              builder.advanceLexer() //Ate ,
            case _ =>
              patternsMarker.done(ScalaElementType.PATTERNS)
              return true
          }
        }
        patternsMarker.done(ScalaElementType.PATTERNS)
        true
      case _ =>
        patternsMarker.rollbackTo()
        false
    }
  }
}