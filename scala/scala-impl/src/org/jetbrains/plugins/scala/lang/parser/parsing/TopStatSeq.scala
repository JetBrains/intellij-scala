package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * TopStatSeq ::= TopStat {semi TopStat}
 */
object TopStatSeq {
  def parse(waitBrace: Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    var semicolonOrNewLineExpected = false

    //NOTE: we might consider not exiting the loop unless the entire sequence was consumed
    //  currently callers of TopStatSeq.parse don't advance lexer anyway...
    var continueLoop = true
    while (continueLoop) {
      val tokenType = builder.getTokenType
      tokenType match {
        case null =>
          continueLoop = false
        case ScalaTokenTypes.tRBRACE if waitBrace =>
          continueLoop = false
        case ScalaTokenTypes.tSEMICOLON =>
          builder.advanceLexer()
          semicolonOrNewLineExpected = false
        case _ if builder.isOutdentHere =>
          continueLoop = false
        case _ if semicolonOrNewLineExpected && !builder.newlineBeforeCurrentToken =>
          builder.error(ScalaBundle.message("semi.expected"))
          continueLoop = false
        case _ =>
          semicolonOrNewLineExpected = false
          val topStatParsed = TopStat.parse()(builder)
          if (!topStatParsed) {
            builder.error(ScalaBundle.message("wrong.top.statement.declaration"))
            builder.advanceLexer()
          }
          else {
            semicolonOrNewLineExpected = true
          }
      }
    }
  }
}