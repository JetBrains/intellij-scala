package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object BlockInIndentationRegion extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3 && builder.isScala3IndentationBasedSyntaxEnabled)

    val blockMarker = builder.mark()

    @tailrec
    def parseNext(needSeparator: Boolean): Unit = {
      builder.getTokenType match {
        case ScalaTokenTypes.kCASE | ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRBRACE | ScalaTokenTypes.tCOMMA =>
        case _ if builder.isOutdentHere =>
        case ScalaTokenTypes.tSEMICOLON =>
          builder.advanceLexer()
          parseNext(needSeparator = false)
        case _ =>
          if (needSeparator && !builder.hasPrecedingIndentation) {
            builder.error(ErrMsg("semi.expected"))
          }
          if (!ResultExpr(stopOnOutdent = true) && !BlockStat()) {
            builder.advanceLexer() // ate something
          }
          parseNext(needSeparator = true)
      }
    }

    parseNext(needSeparator = false)
    blockMarker.done(ScalaElementType.BLOCK)
    true
  }
}