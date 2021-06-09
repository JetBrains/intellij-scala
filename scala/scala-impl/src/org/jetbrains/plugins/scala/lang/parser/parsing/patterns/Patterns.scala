package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/
object Patterns {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val patternsMarker = builder.mark
    if (!Pattern.parse(builder)) {
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
        while (Pattern.parse(builder)) {
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