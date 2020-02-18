package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[TypedFunParam]] ::= id ‘:’ [[Type]]
 */
object TypedFunParam extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        builder.advanceLexer()

        if (builder.getTokenType != ScalaTokenTypes.tCOLON) {
          builder.error(ScalaBundle.message("colon.expected"))
        } else {
          builder.advanceLexer()
          if (!Type.parse(builder)) builder.error(ScalaBundle.message("wrong.type"))
          marker.done(ScalaElementType.PARAM)
        }
        true
      case _ =>
        marker.drop()
        false
    }
  }
}