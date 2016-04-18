package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */
object Block extends Block {
  override protected val blockStat = BlockStat
  override protected val resultExpr = ResultExpr
}

trait Block {
  protected val blockStat: BlockStat
  protected val resultExpr: ResultExpr

  def parse(builder: ScalaPsiBuilder) {
    if (!resultExpr.parse(builder) && blockStat.parse(builder)) {
      var hasSemicolon = false
      var rollbackMarker = builder.mark()

      def updateSemicolon() {
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON =>
            hasSemicolon = true
            while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
              builder.advanceLexer()
            }
          case _ => if (builder.newlineBeforeCurrentToken) hasSemicolon = true
        }
      }

      updateSemicolon()

      while (!resultExpr.parse(builder) && blockStat.parse(builder)) {
        if (!hasSemicolon) {
          rollbackMarker.rollbackTo()
          builder error ErrMsg("semi.expected")
          hasSemicolon = true
          rollbackMarker = builder.mark()
        } else {
          updateSemicolon()
          rollbackMarker.drop()
          rollbackMarker = builder.mark()
        }
      }
      rollbackMarker.drop()
    }
  }

  private def parseImpl(builder: ScalaPsiBuilder): Int = {
    var i: Int = 0

    var tts: List[IElementType] = Nil
    var continue = true

    while (continue) {
      if (resultExpr.parse(builder)) {
        continue = false
        i = i + 1
        tts ::= builder.getTokenType
      } else {
        if (blockStat.parse(builder)) {
          i = i + 1
          tts ::= builder.getTokenType
        } else {
          continue = false
        }
      }
    }
    if (tts.drop(1).headOption.contains(ScalaTokenTypes.tSEMICOLON)) i -= 1  // See unit_to_unit.test

    i
  }

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean): Boolean = parse(builder, hasBrace, needNode = false)

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean, needNode: Boolean): Boolean = {
    if (hasBrace) {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE =>
          builder.advanceLexer()
          builder.enableNewlines
        case _ =>
          blockMarker.drop()
          return false
      }
      ParserUtils.parseLoopUntilRBrace(builder, () => parse(builder))
      builder.restoreNewlinesState
      blockMarker.done(ScalaElementTypes.BLOCK_EXPR)
    }
    else {
      val bm = builder.mark()
      val count = parseImpl(builder)
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