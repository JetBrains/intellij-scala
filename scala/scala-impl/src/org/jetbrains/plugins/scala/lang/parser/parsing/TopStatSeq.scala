package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

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
            hasPackage: Boolean = false): Int = {
    var parseState = ParserState.EMPTY_STATE
    if (waitBrace) parseState = ParserState.FILE_STATE
    if (hasPackage) parseState = ParserState.FILE_STATE
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
        case null => return parseState
        case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //not interesting case
        //otherwise parse TopStat
        case _ =>
          (parseState, TopStat.parse(parseState)(builder)) match {
            case (_, 0) =>
              builder error ScalaBundle.message("wrong.top.statment.declaration")
              builder.advanceLexer()
            case (0, i) =>
              parseState = i % 3
              builder.getTokenType match {
                case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //it is good
                case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
                case null => return parseState
                case _ =>
                  if (!builder.newlineBeforeCurrentToken)
                    builder error ScalaBundle.message("semi.expected")
              }
            case _ =>
              builder.getTokenType match {
                case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //it is good
                case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
                case null => return parseState
                case _ =>
                  if (!builder.newlineBeforeCurrentToken)
                    builder error ScalaBundle.message("semi.expected")

                //else is ok
              }
          }
      }
    }
    parseState
  }
}