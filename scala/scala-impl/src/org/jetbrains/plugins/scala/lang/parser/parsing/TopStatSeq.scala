package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import ParserState._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 05.02.2008
  */

/*
*  TopStatSeq ::= TopStat {semi TopStat}
*/
object TopStatSeq {

  def parse(builder: ScalaPsiBuilder,
            waitBrace: Boolean = true,
            hasPackage: Boolean = false): ParserState = {
    var parseState: ParserState = EMPTY_STATE
    if (waitBrace || hasPackage) {
      parseState = FILE_STATE
    }
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
        case null => return parseState
        case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //not interesting case
        //otherwise parse TopStat
        case _ =>
          TopStat.parse(parseState)(builder) match {
            case Some(EMPTY_STATE) =>
              builder error ScalaBundle.message("wrong.top.statment.declaration")
              builder.advanceLexer()
            case newState =>
              if (parseState == EMPTY_STATE) {
                parseState = newState.getOrElse(EMPTY_STATE)
              }
              builder.getTokenType match {
                case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //it is good
                case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
                case null => return parseState
                case _ =>
                  if (!builder.newlineBeforeCurrentToken)
                    builder error ScalaBundle.message("semi.expected")
              }
          }
      }
    }
    parseState
  }
}