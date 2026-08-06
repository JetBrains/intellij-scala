package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern

/** may consider unifying with [[org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Spliced]] in future */
object SplicedPatternExpr extends ParsingRule {
  def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    assert(builder.getTokenType == ScalaTokenType.SpliceStart)
    builder.advanceLexer() //eat '

    //TODO: splice can can go without `{` for example: '{ $argsExpr.sum }
    assert(builder.getTokenType == ScalaTokenTypes.tLBRACE) // should be handled by the lexer
    builder.advanceLexer() //eat {

    Pattern()
    consumeEverythingUntilClosingBrace()

    marker.done(ScalaElementType.SPLICED_PATTERN_EXPR)
    true
  }

  //handling of broken pattern.
  // Example: case '{ foo(${_*}) } => ???
  // ("_*" is invalid)
  //if we consumed opening '{' we need consume everything until the closing '}'
  private def consumeEverythingUntilClosingBrace()(implicit builder: ScalaPsiBuilder): Unit = {
    if (builder.getTokenType != ScalaTokenTypes.tRBRACE) {
      builder.error(ScalaBundle.message("pattern.expected"))
      //eat all until {
      while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tRBRACE) {
        builder.advanceLexer()
      }
    }
    if (builder.getTokenType == ScalaTokenTypes.tRBRACE)
      builder.advanceLexer() //eat }
  }
}
