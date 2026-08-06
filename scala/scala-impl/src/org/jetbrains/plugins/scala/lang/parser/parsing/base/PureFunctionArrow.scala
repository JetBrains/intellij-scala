package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object PureFunctionArrow {
  def remapCurrentToken()(implicit builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.features.`parses capture checking` && (
      builder.getTokenText match {
        case "->" =>
          builder.remapCurrentToken(ScalaTokenType.PureFunctionArrow)
          true
        case "?->" =>
          builder.remapCurrentToken(ScalaTokenType.ImplicitPureFunctionArrow)
          true
        case _ =>
          false
      }
    )
  }

  def isTokenText(@Nullable text: String): Boolean = text == "->" || text == "?->"
}
