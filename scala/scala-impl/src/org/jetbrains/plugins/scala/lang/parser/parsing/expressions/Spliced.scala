package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

abstract class Spliced(elementType: ScalaElementType) extends ParsingRule {
  final def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    assert(builder.getTokenType == ScalaTokenType.SpliceStart)
    builder.advanceLexer()
    assert(builder.getTokenType == ScalaTokenTypes.tLBRACE) // should be handled by the lexer
    builder.advanceLexer()

    ParserUtils.parseLoopUntilRBrace() {
      Block.ContentInBraces()
    }

    marker.done(elementType)
    true
  }
}

object SplicedExpr extends Spliced(ScalaElementType.SPLICED_BLOCK_EXPR)

object SplicedType extends Spliced(ScalaElementType.SPLICED_BLOCK_TYPE)