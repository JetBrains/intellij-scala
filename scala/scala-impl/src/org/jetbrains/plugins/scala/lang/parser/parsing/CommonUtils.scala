package org.jetbrains.plugins.scala
package lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.{BlockIndentation, ScalaElementType}

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
object CommonUtils {

  def parseInterpolatedString(builder: ScalaPsiBuilder, isPattern: Boolean): Unit = {
    val prefixMarker = builder.mark()
    builder.advanceLexer()
    prefixMarker.done(
      if (isPattern) ScalaElementType.INTERPOLATED_PREFIX_PATTERN_REFERENCE
      else ScalaElementType.INTERPOLATED_PREFIX_LITERAL_REFERENCE)
    val patternArgsMarker = builder.mark()
    while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END) {
      if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
        builder.advanceLexer()
        if (isPattern) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
              val idMarker = builder.mark()
              builder.advanceLexer()
            idMarker.done(ScalaElementType.REFERENCE_PATTERN)
          } else if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
            builder.advanceLexer()
            if (!Pattern.parse(builder)) builder.error(ScalaBundle.message("wrong.pattern"))
            else if (builder.getTokenType != ScalaTokenTypes.tRBRACE) {
              builder.error(ScalaBundle.message("right.brace.expected"))
              ParserUtils.parseLoopUntilRBrace(builder, () => (), braceReported = true)
            } else builder.advanceLexer()
          }
        } else if (!BlockExpr.parse(builder)) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementType.REFERENCE_EXPRESSION)
          } else if (builder.getTokenType == ScalaTokenTypes.kTHIS) {
            val literalMarker = builder.mark()
            builder.advanceLexer()
            literalMarker.done(ScalaElementType.THIS_REFERENCE)
          } else if (!builder.getTokenText.startsWith("$")) builder.error(ScalaBundle.message("bad.interpolated.string.injection"))
        }
      } else {
        if (builder.getTokenType == ScalaTokenTypes.tWRONG_STRING) builder.error(ScalaBundle.message("wrong.string.literal"))
        builder.advanceLexer()
      }
    }
    if (isPattern) patternArgsMarker.done(ScalaElementType.PATTERN_ARGS)
    else patternArgsMarker.drop()
    if (!builder.eof()) builder.advanceLexer()
  }

  def eatAllSemicolons(builder: ScalaPsiBuilder): Unit = {
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      builder.advanceLexer()
    }
  }

  def eatAllSemicolons(builder: ScalaPsiBuilder, blockIndentation: BlockIndentation): Unit = {
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      blockIndentation.fromHere()(builder)
      builder.advanceLexer()
    }
  }
}
