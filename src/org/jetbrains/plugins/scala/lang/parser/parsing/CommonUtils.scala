package org.jetbrains.plugins.scala
package lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
object CommonUtils {
  def parseInterpolatedString(builder: ScalaPsiBuilder, isPattern: Boolean) = {
    val prefixMarker = builder.mark()
    builder.advanceLexer()
    prefixMarker done ScalaElementTypes.INTERPOLATED_PREFIX_REFERENCE
    val patternArgsMarker = builder.mark()
    while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END){
      if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
        builder.advanceLexer()
        if (!BlockExpr.parse(builder, isPattern=isPattern)) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(if (isPattern) ScalaElementTypes.REFERENCE_PATTERN else ScalaElementTypes.REFERENCE_EXPRESSION)
          } else if (builder.getTokenType == ScalaTokenTypes.kTHIS && !isPattern) {
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
