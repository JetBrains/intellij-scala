package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object End extends ParsingRule {
  private val isAllowedEndToken = Set(
    ScalaTokenTypes.kIF,
    ScalaTokenTypes.kWHILE,
    ScalaTokenTypes.kFOR,
    ScalaTokenTypes.kMATCH,
    ScalaTokenTypes.kTRY,
    ScalaTokenType.NewKeyword,
    ScalaTokenType.GivenKeyword,
    ScalaTokenType.ExtensionKeyword,
    ScalaTokenTypes.tIDENTIFIER,
  )

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3)
      return false

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
          builder.getTokenText == "end") {
      val marker = builder.mark()
      builder.remapCurrentToken(ScalaTokenType.EndKeyword)
      builder.advanceLexer()

      if (isAllowedEndToken(builder.getTokenType)) {
        builder.advanceLexer()
        marker.done(ScalaElementType.END_STMT)
        true
      } else {
        marker.rollbackTo()
        false
      }
    } else false
  }
}
