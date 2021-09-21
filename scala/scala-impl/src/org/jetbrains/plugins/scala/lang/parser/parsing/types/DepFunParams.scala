package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[DepFunParams]] ::= '(' [[TypedFunParam]], { [[TypedFunParam]] } ')'
 */
object DepFunParams extends ParsingRule {
  def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => builder.advanceLexer()
      case _                             => marker.drop(); return false
    }

    if (!TypedFunParam()) {
      marker.rollbackTo()
      false
    } else {
      var exit = false
      while (!exit &&
        builder.getTokenType == ScalaTokenTypes.tCOMMA &&
        !builder.consumeTrailingComma(ScalaTokenTypes.tLPARENTHESIS)) {
        builder.advanceLexer()
        if (!TypedFunParam()) exit = true
      }

      builder.getTokenType match {
        case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer()
        case _                             => builder.error(ScalaBundle.message("rparenthesis.expected"))
      }

      marker.done(ScalaElementType.PARAM_CLAUSE)
      true
    }
  }
}