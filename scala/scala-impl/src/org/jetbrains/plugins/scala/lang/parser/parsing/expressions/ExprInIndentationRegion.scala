package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

trait ExprInIndentationRegion extends ParsingRule {
  protected def exprKind: ParsingRule

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3 || builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      return exprKind()
    }

    val indentationForExprBlock = builder.findPreviousIndent match {
      case Some(indent) => indent
      case None => return exprKind()
    }

    builder.withIndentationWidth(indentationForExprBlock) {

      val blockMarker = builder.mark()
      blockMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_WS_AND_COMMENT_TOKENS, null)
      if (!exprKind()) {
        BlockStat.parse(builder)
      }

      @tailrec
      def parseRest(isBlock: Boolean): Boolean = {
        val isInside = builder.findPreviousIndent.exists(_ >= indentationForExprBlock)
        if (isInside) {
          val tt = builder.getTokenType
          if (tt == ScalaTokenTypes.tSEMICOLON) {
            builder.advanceLexer() // ate ;
          } else if (builder.eof() || tt == ScalaTokenTypes.tRPARENTHESIS || tt == ScalaTokenTypes.tRBRACE) {
            return isBlock
          } else if (!BlockStat.parse(builder)) {
            builder.advanceLexer() // ate something
          }
          parseRest(isBlock = true)
        } else {
          isBlock
        }
      }

      if (parseRest(isBlock = false)) {
        End()
        blockMarker.done(ScalaElementType.BLOCK)
      } else {
        blockMarker.drop()
      }

      true
    }
  }
}

object ExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprKind: ParsingRule = Expr
}

object PostfixExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprKind: ParsingRule = PostfixExpr
}