package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.{InScala3, ParserUtils}

/**
 * [[MatchTypeSuffix]] ::= [[InfixType]] `match` [[TypeCaseClauses]]
 */
object MatchTypeSuffix extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {

    def parseCases(): Unit =
      if (!TypeCaseClauses.parse(builder))
        builder.error(ScalaBundle.message("match.type.cases.expected"))

    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer()
        builder.enableNewlines()
        ParserUtils.parseLoopUntilRBrace(builder, parseCases)
        builder.restoreNewlinesState()
      case InScala3(ScalaTokenTypes.kCASE) =>

        builder.findPreviousIndent match {
          case Some(indentationWidth) =>
            builder.withIndentationWidth(indentationWidth) {
              TypeCaseClauses()
            }
          case None =>
            builder.error(ScalaBundle.message("expected.case.on.a.new.line"))
        }
      case _ => builder.error(ScalaBundle.message("match.type.cases.expected"))
    }
    true
  }
}
