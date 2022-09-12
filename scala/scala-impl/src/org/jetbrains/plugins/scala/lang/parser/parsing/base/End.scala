package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{IndentationWidth, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object End {
  private val isAllowedEndToken = Set(
    ScalaTokenTypes.kVAL,
    ScalaTokenTypes.kIF,
    ScalaTokenTypes.kWHILE,
    ScalaTokenTypes.kFOR,
    ScalaTokenTypes.kMATCH,
    ScalaTokenTypes.kTRY,
    ScalaTokenTypes.kTHIS,
    ScalaTokenType.NewKeyword,
    ScalaTokenType.GivenKeyword,
    ScalaTokenType.ExtensionKeyword,
    ScalaTokenTypes.tIDENTIFIER,
  )

  //override def parse(implicit builder: ScalaPsiBuilder): Boolean = apply(builder.currentIndentationWidth)

  def apply(targetIndentationWidth: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3)
      return false

    if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER &&
          builder.getTokenText == "end" &&
          isAllowedEndToken(builder.lookAhead(1)) &&
          builder.findPreviousIndent.contains(targetIndentationWidth)) {
      val marker = builder.mark()
      builder.remapCurrentToken(ScalaTokenType.EndKeyword)
      builder.advanceLexer() // ate end

      if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.getTokenText == ScalaTokenType.ExtensionKeyword.keywordText) {
        builder.remapCurrentToken(ScalaTokenType.ExtensionKeyword)
      }

      builder.advanceLexer() // ate end-token
      marker.done(ScalaElementType.END_STMT)
      true
    } else false
  }
}
