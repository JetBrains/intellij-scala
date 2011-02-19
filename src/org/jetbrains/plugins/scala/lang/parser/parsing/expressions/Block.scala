package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder
import annotation.tailrec
import util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */

object Block {

  def parse(builder: ScalaPsiBuilder) {
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
      val rollMarker = builder.mark
      if (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
        rollMarker.rollbackTo
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer
          }
          case _ => {
            if (!builder.newlineBeforeCurrentToken)
              builder error ErrMsg("semi.expected")
          }
        }
      } else {
        rollMarker.rollbackTo
      }
    }
  }

  private def parseImpl(builder: ScalaPsiBuilder): Int = {
    var i: Int = 0;
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
      val t = builder.getTokenType
      if (t != ScalaTokenTypes.tSEMICOLON) {
        i = i + 1;
      }
    }
    i
  }

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean): Boolean = parse(builder, hasBrace, false)
  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean, needNode: Boolean): Boolean = {
    if (hasBrace) {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE => {
          builder.advanceLexer
          builder.enableNewlines
        }
        case _ => {
          blockMarker.drop
          return false
        }
      }
      ParserUtils.parseLoopUntilRBrace(builder, () => parse(builder))
      builder.restoreNewlinesState
      blockMarker.done(ScalaElementTypes.BLOCK_EXPR)
    }
    else {
      val bm = builder.mark()
      if (parseImpl(builder) > 1) {
        bm.done(ScalaElementTypes.BLOCK)
      } else {
        if (!needNode) bm.drop else bm.done(ScalaElementTypes.BLOCK)
      }
    }
    return true
  }
}