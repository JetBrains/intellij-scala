package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 *  [[MatchTypeSuffix]] ::= 'match' [[TypeCaseClauses]]
 */
object MatchTypeSuffix extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    def parseCases(): Unit =
      if (!TypeCaseClauses.parse())
        builder.error(ScalaBundle.message("match.type.cases.expected"))

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
        marker.drop()
        true
      case _ =>
        marker.rollbackTo()
        false
    }
  }
}
