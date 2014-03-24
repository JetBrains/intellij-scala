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
import com.intellij.psi.tree.IElementType

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */

object Block {

  def parse(builder: ScalaPsiBuilder, isPattern: Boolean) {
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder, isPattern)) {
      val rollMarker = builder.mark
      if (!ResultExpr.parse(builder) && BlockStat.parse(builder, isPattern)) {
        rollMarker.rollbackTo()
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer()
          }
          case _ => {
            if (!builder.newlineBeforeCurrentToken)
              builder error ErrMsg("semi.expected")
          }
        }
      } else {
        rollMarker.rollbackTo()
      }
    }
  }

  private def parseImpl(builder: ScalaPsiBuilder, isPattern: Boolean): Int = {
    var i: Int = 0

    var tts: List[IElementType] = Nil
    var continue = true

    while (continue) {
      if (ResultExpr.parse(builder)) {
        continue = false
        i = i + 1
        tts ::= builder.getTokenType
      } else {
        if (BlockStat.parse(builder, isPattern)) {
          i = i + 1
          tts ::= builder.getTokenType
        } else {
          continue = false
        }
      }
    }
    if (tts.drop(1).headOption == Some(ScalaTokenTypes.tSEMICOLON)) i -= 1  // See unit_to_unit.test

    i
  }

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean, isPattern: Boolean): Boolean = parse(builder, hasBrace, needNode = false, isPattern)

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean, needNode: Boolean, isPattern: Boolean): Boolean = {
    if (hasBrace) {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE => {
          builder.advanceLexer()
          builder.enableNewlines
        }
        case _ => {
          blockMarker.drop()
          return false
        }
      }
      ParserUtils.parseLoopUntilRBrace(builder, () => parse(builder, isPattern))
      builder.restoreNewlinesState
      blockMarker.done(ScalaElementTypes.BLOCK_EXPR)
    }
    else {
      val bm = builder.mark()
      val count = parseImpl(builder, isPattern)
      if (count > 1) {
        bm.done(ScalaElementTypes.BLOCK)
      } else {
        if (!needNode) bm.drop() else bm.done(ScalaElementTypes.BLOCK)
//        bm.done(ScalaElementTypes.BLOCK)
      }
    }
    true
  }
}