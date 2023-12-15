package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{BlockIndentation, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object BlockInIndentationRegion extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3 && builder.isScala3IndentationBasedSyntaxEnabled)

    val blockMarker = builder.mark()

    val indentionWidthBefore = builder.currentIndentationWidth

    def hasOutdent: Boolean = {
      builder.findPreviousIndent.exists(_ <= indentionWidthBefore)
    }

    val blockIndentation = BlockIndentation.create

    @tailrec
    def parseNext(): Unit = {
      blockIndentation.fromHere()
      builder.getTokenType match {
        case _ if hasOutdent || builder.eof() =>
          return

        case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRBRACE =>
          return

        case ScalaTokenTypes.kCASE =>
          return

        case ScalaTokenTypes.tSEMICOLON =>
          builder.advanceLexer()

        case _ =>
          if (!BlockStat()) {
            builder.advanceLexer() // ate something
          }
      }

      parseNext()
    }

    parseNext()
    blockIndentation.drop()
    blockMarker.done(ScalaElementType.BLOCK)
    true
  }
}