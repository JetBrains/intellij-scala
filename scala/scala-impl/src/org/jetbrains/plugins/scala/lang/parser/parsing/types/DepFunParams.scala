package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[DepFunParams]] ::= '(' [[TypedFunParam]], { [[TypedFunParam]] } ')'
 */
object DepFunParams {
  def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => builder.advanceLexer()
      case _                             => marker.drop(); return false
    }

    if (!TypedFunParam.parse) {
      marker.rollbackTo()
      false
    } else {
      var exit = false
      while (!exit &&
        builder.getTokenType == ScalaTokenTypes.tCOMMA &&
        !builder.consumeTrailingComma(ScalaTokenTypes.tLPARENTHESIS)) {
        builder.advanceLexer()
        if (!TypedFunParam.parse) exit = true
      }

      builder.getTokenType match {
        case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer()
        case _                             => builder.error("rparenthesis.expected")
      }

      marker.done(ScalaElementType.PARAM_CLAUSE)
      true
    }
  }
}