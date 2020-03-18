package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[MatchType]] ::= [[InfixType]] [[MatchTypeSuffix]]
 */
object MatchType {
  def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (!InfixType.parse(builder)) {
      marker.rollbackTo()
      false
    } else // todo: handle indention
      builder.getTokenType match {
        case ScalaTokenTypes.kMATCH =>
          builder.advanceLexer()
          MatchTypeSuffix.parse(builder)
          marker.done(ScalaElementType.MATCH_TYPE)
          true
        case _ => false
      }
  }
}
