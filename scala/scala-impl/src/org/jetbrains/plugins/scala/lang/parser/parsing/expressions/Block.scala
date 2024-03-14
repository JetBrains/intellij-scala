package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

import scala.annotation.tailrec

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */
object Block {
  abstract class ContentInBraces extends ParsingRule {
    def parseStmt()(implicit builder: ScalaPsiBuilder): Boolean

    override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
      while(builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
        builder.advanceLexer()
      }

      @tailrec
      def parseNextStmt(): Unit = {
        val hasSemicolon =
          builder.getTokenType match {
            case ScalaTokenTypes.tSEMICOLON =>
              while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
                builder.advanceLexer()
              }
              true
            case _ =>
              builder.newlineBeforeCurrentToken
          }

        if (hasSemicolon) {
          if (parseStmt()) {
            parseNextStmt()
          }
        } else {
          val rollbackMarker = builder.mark()
          if (parseStmt()) {
            // we were able to parse another statement, but there should have been an error before that
            // so we rollback, insert the error, and parse the same statement again
            rollbackMarker.rollbackTo()
            builder error ErrMsg("semi.expected")
            parseStmt()
            parseNextStmt()
          } else {
            rollbackMarker.drop()
          }
        }
      }

      if (parseStmt()) {
        parseNextStmt()
      }
      true
    }
  }

  object ContentInBraces extends ContentInBraces {
    override def parseStmt()(implicit builder: ScalaPsiBuilder): Boolean =
      !ResultExpr(stopOnOutdent = false) && BlockStat()
  }

  object Braced extends ParsingRule {
    override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
      val blockMarker = builder.mark()
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE =>
          builder.advanceLexer()
          builder.enableNewlines()
        case _ =>
          blockMarker.drop()
          return false
      }

      builder.withIndentationRegion(builder.newBracedIndentationRegionHere) {
        ParserUtils.parseLoopUntilRBrace() {
          Block.ContentInBraces()
        }
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
      if (needNode) {
        bm.done(ScalaElementType.BLOCK)
        true
      } else {
        bm.drop()
        count > 0
      }
    }

    private def parseImpl(stopOnOutdent: Boolean)(implicit builder: ScalaPsiBuilder): Int = {
      var i: Int = 0

      var tts: List[IElementType] = Nil
      var continue = true

      while (continue) {
        val isOutdent =
          stopOnOutdent &&
          builder.isScala3 &&
          builder.isScala3IndentationBasedSyntaxEnabled &&
            builder.isOutdentHere

        builder.ignoreOutdent()

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
      i
    }
  }
}
