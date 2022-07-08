package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object RefineStatSeq extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    parseStats()
    true
  }

  private def parseStats()(implicit builder: ScalaPsiBuilder): Unit = {
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE | null => return
        case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //not interesting case
        //otherwise parse TopStat
        case _ =>
          if (!RefineStat()) {
            builder error ScalaBundle.message("wrong.top.statement.declaration")
            return
          }
          else {
            builder.getTokenType match {
              case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //it is good
              case null | ScalaTokenTypes.tRBRACE => return
              case _ if !builder.newlineBeforeCurrentToken => builder error ScalaBundle.message("semi.expected")
              case _ =>
            }
          }
      }
    }
  }
}