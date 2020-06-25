package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 * [[MatchTypeSuffix]] ::= [[InfixType]] `match` [[TypeCaseClauses]]
 */
object MatchTypeSuffix {
  def parse(builder: ScalaPsiBuilder): Boolean = {

    def parseCases(): Unit =
      if (!TypeCaseClauses.parse(builder))
        builder.error(ScalaBundle.message("match.type.cases.expected"))

    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer()
        builder.enableNewlines()
        ParserUtils.parseLoopUntilRBrace(builder, parseCases)
        builder.restoreNewlinesState()
      case _ => builder.error(ScalaBundle.message("match.type.cases.expected"))
    }
    true
  }
}
