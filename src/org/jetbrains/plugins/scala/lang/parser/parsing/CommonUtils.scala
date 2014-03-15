package org.jetbrains.plugins.scala
package lang.parser.parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import com.intellij.lang.PsiBuilder

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
object CommonUtils {

  def parseInterpolatedString(builder: ScalaPsiBuilder, isPattern: Boolean) = {
    val prefixMarker = builder.mark()
    builder.advanceLexer()
    prefixMarker done ScalaElementTypes.INTERPOLATED_PREFIX_REFERENCE
    var patternArgsMarker: Option[PsiBuilder.Marker] = None
    if (isPattern) {
      patternArgsMarker = Option(builder.mark())
    }
    while (!builder.eof() && builder.getTokenType != ScalaTokenTypes.tINTERPOLATED_STRING_END){
      if (builder.getTokenType == ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION) {
        builder.advanceLexer()
        if (!BlockExpr.parse(builder)) {
          if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER) {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(if (isPattern) ScalaElementTypes.REFERENCE_PATTERN else ScalaElementTypes.REFERENCE_EXPRESSION)
          } else if (builder.getTokenType == ScalaTokenTypes.kTHIS) {
            val literalMarker = builder.mark()
            builder.advanceLexer()
            literalMarker.done(ScalaElementTypes.THIS_REFERENCE)
          } else {
            if (!builder.getTokenText.startsWith("$")) builder.error("Bad interpolated string injection")
          }
        }
      } else {
        if (builder.getTokenType == ScalaTokenTypes.tWRONG_STRING) {
          builder.error("Wrong string literal")
        }
        builder.advanceLexer()
      }
    }
    patternArgsMarker.map{_.done(ScalaElementTypes.PATTERN_ARGS)}
    if (!builder.eof()) builder.advanceLexer()
  }
}
