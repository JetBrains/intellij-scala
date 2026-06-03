package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[TypedFunParam]] ::= id ‘:’ [[Type]]
 */
object TypedFunParam extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    val modifierMarker = builder.mark()
    val hasModifier = builder.isScala3 &&
      builder.lookAhead(1, ScalaTokenTypes.tIDENTIFIER) &&
      builder.tryParseSoftKeyword(ScalaTokenType.ErasedKeyword)
    if (hasModifier) modifierMarker.done(ScalaElementType.MODIFIERS)
    else modifierMarker.drop()

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()
        if (builder.getTokenType != ScalaTokenTypes.tCOLON) {
          marker.rollbackTo()
          false
        } else {
          builder.advanceLexer()
          if (!ParamType()) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.PARAM)
          true
        }
      case _ =>
        marker.drop()
        false
    }
  }
}
