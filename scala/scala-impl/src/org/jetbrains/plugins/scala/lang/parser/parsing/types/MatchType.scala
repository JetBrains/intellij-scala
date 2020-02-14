package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 * MatchType ::= InfixType `match` TypeCaseClauses
 */
object MatchType {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    def parseCases(): Unit =
      if (!TypeCaseClauses.parse(builder))
        builder.error(ScalaBundle.message("match.type.cases.expected"))

    if (!InfixType.parse(builder)) {
      marker.rollbackTo()
      false
    } else {
      builder.getTokenType match {
        case ScalaTokenTypes.kMATCH =>
          builder.advanceLexer()
          builder.getTokenType match {
            case ScalaTokenTypes.tLBRACE =>
              builder.advanceLexer()
              builder.enableNewlines()
              ParserUtils.parseLoopUntilRBrace(builder, parseCases)
              builder.restoreNewlinesState()
            case _ => builder.error(ScalaBundle.message("match.type.cases.expected"))
          }
          marker.done(ScalaElementType.MATCH_TYPE)
          true
        case _ =>
          marker.rollbackTo()
          false
      }
    }
  }

  def apply(builder: ScalaPsiBuilder): Boolean = parse(builder)
}
