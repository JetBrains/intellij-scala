package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object BlockInIndentationRegion extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3 && builder.isScala3IndentationBasedSyntaxEnabled)

    val blockMarker = builder.mark()

    @tailrec
    def parseNext(): Unit = {
      builder.getTokenType match {
        case ScalaTokenTypes.kCASE =>
          return

        case _ if builder.isOutdentHere =>
          return

        case _ if builder.hasPrecedingIndent && !builder.isIndentHere =>
          // the block must be indented one more than the indentation of `case`
          //
          // x match
          //   case 1 =>
          //   1   // <- no outdent here, but still not a part of the case block
          return

        case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRBRACE =>
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
    blockMarker.done(ScalaElementType.BLOCK)
    true
  }
}