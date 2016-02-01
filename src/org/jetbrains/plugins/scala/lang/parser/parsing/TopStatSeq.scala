package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
*  TopStatSeq ::= TopStat {semi TopStat}
*/
object TopStatSeq extends TopStatSeq {
  override protected val topStat = TopStat
}

trait TopStatSeq {
  protected val topStat: TopStat

  def parse(builder: ScalaPsiBuilder): Int = parse(builder, waitBrace = false)
  def parse(builder: ScalaPsiBuilder, waitBrace: Boolean): Int = parse(builder, waitBrace, hasPackage = false)
  def parse(builder: ScalaPsiBuilder, waitBrace: Boolean, hasPackage: Boolean): Int = {
    var parseState = ParserState.EMPTY_STATE
    if (waitBrace) parseState = ParserState.FILE_STATE
    if(hasPackage) parseState = ParserState.FILE_STATE
    while (true) {
      builder.getTokenType match {
        //end of parsing when find } or builder.eof
        case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
        case null => return parseState
        case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer() //not interesting case
        //otherwise parse TopStat
        case _ =>
          def error = {
            builder error ScalaBundle.message("wrong.top.statment.declaration")
            builder.advanceLexer
          }
          (parseState, topStat.parse(builder, parseState)) match {
            case (_, 0) => error
            case (0, i) => {
              parseState = i % 3
              builder.getTokenType match {
                case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer //it is good
                case ScalaTokenTypes.tRBRACE if waitBrace => return parseState
                case null => return parseState
                case _ =>
                  if (!builder.newlineBeforeCurrentToken)
                    builder error ScalaBundle.message("semi.expected")
              }
            }
            case _ => {
              builder.getTokenType match {
                case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer //it is good
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
    }
    return parseState
  }
}