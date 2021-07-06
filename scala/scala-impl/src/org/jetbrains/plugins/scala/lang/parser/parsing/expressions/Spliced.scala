package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

object Spliced {
  def parse(builder: ScalaPsiBuilder, inType: Boolean): Unit = {
    val marker = builder.mark()
    assert(builder.getTokenType == ScalaTokenType.SpliceStart)
    builder.advanceLexer()
    assert(builder.getTokenType == ScalaTokenTypes.tLBRACE) // should be handled by the lexer
    builder.advanceLexer()

    ParserUtils.parseLoopUntilRBrace(builder, () => Block.parse(builder))

    marker.done(if (inType) ScalaElementType.SPLICED_BLOCK_TYPE else ScalaElementType.SPLICED_BLOCK_EXPR)
  }
}
