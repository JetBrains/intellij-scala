package org.jetbrains.plugins.scala
package lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.{BlockIndentation, ScalaElementType}

object CommonUtils {

  def parseInterpolatedString(isPattern: Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    val prefixMarker = builder.mark()
    builder.advanceLexer()
    val prefixType =
      if (isPattern) ScalaElementType.INTERPOLATED_PREFIX_PATTERN_REFERENCE
      else ScalaElementType.INTERPOLATED_PREFIX_LITERAL_REFERENCE
    prefixMarker.done(prefixType)

    val patternArgsMarker = builder.mark()
    while (!builder.eof() &&
      builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END &&
      builder.getTokenType != ScalaTokenTypes.tWRONG_LINE_BREAK_IN_STRING
    ) {

      remapRawStringTokens(builder)

      if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
        builder.advanceLexer()
        if (isPattern) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementType.REFERENCE_PATTERN)
          }
          else if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
            builder.advanceLexer()
            if (!Pattern())
              builder.error(ScalaBundle.message("wrong.pattern"))
            else if (builder.getTokenType != ScalaTokenTypes.tRBRACE) {
              builder.error(ScalaBundle.message("right.brace.expected"))
              ParserUtils.parseLoopUntilRBrace(braceReported = true) {}
            }
            else
              builder.advanceLexer()
          }
        }
        else if (!BlockExpr()) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementType.REFERENCE_EXPRESSION)
          }
          else if (builder.getTokenType == ScalaTokenTypes.kTHIS) {
            val literalMarker = builder.mark()
            builder.advanceLexer()
            literalMarker.done(ScalaElementType.THIS_REFERENCE)
          }
          else if (!builder.getTokenText.startsWith("$"))
            builder.error(ScalaBundle.message("bad.interpolated.string.injection"))
        }
      }
      else {
        // TODO: are these 2 dead branches? they are not triggered by tests
        builder.getTokenType match {
          case ScalaTokenTypes.tWRONG_STRING =>
            builder.error(ScalaBundle.message("wrong.string.literal"))
          case ScalaTokenTypes.tWRONG_LINE_BREAK_IN_STRING =>
            builder.error(ScalaBundle.message("wrong.string.literal"))
          case _ =>
        }

        builder.advanceLexer()
      }
    }

    if (isPattern) patternArgsMarker.done(ScalaElementType.PATTERN_ARGS)
    else patternArgsMarker.drop()

    // FIXME: non-interpolated strings behave a little differently
    //  e.g. unclosed string literal s"\" vs "\"
    if (builder.eof() || builder.getTokenType == ScalaTokenTypes.tWRONG_LINE_BREAK_IN_STRING) {
      builder.error(ScalaBundle.message("end.of.string.expected"))
    }

    if (!builder.eof())
      builder.advanceLexer()
  }

  /** see comments to [[ScalaTokenTypes.tINTERPOLATED_RAW_STRING]] and [[ScalaTokenTypes.tINTERPOLATED_MULTILINE_RAW_STRING]] */
  def remapRawStringTokens(builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case ScalaTokenTypes.tINTERPOLATED_RAW_STRING =>
        builder.remapCurrentToken(ScalaTokenTypes.tINTERPOLATED_STRING)
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_RAW_STRING =>
        builder.remapCurrentToken(ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING)
      case _ =>
    }

  def eatAllSemicolons()(implicit builder: ScalaPsiBuilder): Unit = {
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      builder.advanceLexer()
    }
  }

  def eatAllSemicolons(blockIndentation: BlockIndentation)(implicit builder: ScalaPsiBuilder): Unit = {
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      blockIndentation.fromHere()
      builder.advanceLexer()
    }
  }
}
