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
object Block {
  object ContentInBraces extends ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
      if (!ResultExpr(stopOnOutdent = false) && BlockStat()) {
        var hasSemicolon = false
        var rollbackMarker = builder.mark()

        def updateSemicolon(): Unit = {
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

        while (!ResultExpr(stopOnOutdent = false) && BlockStat()) {
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
      true
    }
  }

  object Braced extends ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE =>
          builder.advanceLexer()
          builder.enableNewlines()
        case _ =>
          blockMarker.drop()
          return false
      }
      ParserUtils.parseLoopUntilRBrace() {
        Block.ContentInBraces()
      }
      builder.restoreNewlinesState()
      blockMarker.done(ScCodeBlockElementType.BlockExpression)
      true
    }
  }

  object Braceless {
    def apply(stopOnOutdent: Boolean, needNode: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
      val bm = builder.mark()
      val count = parseImpl(stopOnOutdent)
      if (count > 1 || needNode) {
        bm.done(ScalaElementType.BLOCK)
      } else {
        bm.drop()
      }
      true
    }

    private def parseImpl(stopOnOutdent: Boolean)(implicit builder: ScalaPsiBuilder): Int = {
      var i: Int = 0
      val blockIndentation = BlockIndentation.create

      var tts: List[IElementType] = Nil
      var continue = true

      val prevIndentation = builder.currentIndentationWidth
      while (continue) {
        blockIndentation.fromHere()
        val isOutdent =
          stopOnOutdent &&
          builder.isScala3 &&
          builder.isScala3IndentationBasedSyntaxEnabled &&
            builder.findPreviousIndent.exists(_ < prevIndentation)
        if (isOutdent) {
          continue = false
        } else if (ResultExpr(stopOnOutdent)) {
          continue = false
          i = i + 1
          tts ::= builder.getTokenType
        } else if (BlockStat()) {
          i = i + 1
          tts ::= builder.getTokenType
        } else {
          continue = false
        }
      }
      if (tts.drop(1).headOption.contains(ScalaTokenTypes.tSEMICOLON)) i -= 1  // See unit_to_unit.test
      blockIndentation.drop()
      i
    }
  }
}