package org.jetbrains.plugins.scala
package lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
object CommonUtils extends CommonUtils {
  override protected val blockExpr = BlockExpr
  override protected val pattern = Pattern
}

trait CommonUtils {
  protected val blockExpr: BlockExpr
  protected val pattern: Pattern

  def parseInterpolatedString(builder: ScalaPsiBuilder, isPattern: Boolean) = {
    val prefixMarker = builder.mark()
    builder.advanceLexer()
    prefixMarker.done(
      if (isPattern) ScalaElementTypes.INTERPOLATED_PREFIX_PATTERN_REFERENCE
      else ScalaElementTypes.INTERPOLATED_PREFIX_LITERAL_REFERENCE)
    val patternArgsMarker = builder.mark()
    while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END) {
      if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
        builder.advanceLexer()
        if (isPattern) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
              val idMarker = builder.mark()
              builder.advanceLexer()
              idMarker.done(ScalaElementTypes.REFERENCE_PATTERN)
          } else if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
            builder.advanceLexer()
            if (!pattern.parse(builder)) builder.error("Wrong pattern")
            else if (builder.getTokenType != ScalaTokenTypes.tRBRACE) {
              builder.error("'}' is expected")
              ParserUtils.parseLoopUntilRBrace(builder, () => (), braceReported = true)
            } else builder.advanceLexer()
          }
        } else if (!blockExpr.parse(builder)) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
          } else if (builder.getTokenType == ScalaTokenTypes.kTHIS) {
            val literalMarker = builder.mark()
            builder.advanceLexer()
            literalMarker.done(ScalaElementTypes.THIS_REFERENCE)
          } else if (!builder.getTokenText.startsWith("$")) builder.error("Bad interpolated string injection")
        }
      } else {
        if (builder.getTokenType == ScalaTokenTypes.tWRONG_STRING) builder.error("Wrong string literal")
        builder.advanceLexer()
      }
    }
    if (isPattern) patternArgsMarker.done(ScalaElementTypes.PATTERN_ARGS)
    else patternArgsMarker.drop()
    if (!builder.eof()) builder.advanceLexer()
  }
}
