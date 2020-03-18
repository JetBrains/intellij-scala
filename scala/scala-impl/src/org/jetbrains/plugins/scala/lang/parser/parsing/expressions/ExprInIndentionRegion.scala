package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

trait ExprInIndentionRegion extends ParsingRule {
  protected def exprKind: ParsingRule

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3 || builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      return exprKind()
    }

    val indentionForExprBlock = builder.findPreviousIndent match {
      case Some(indent) => indent
      case None => return exprKind()
    }

    builder.withIndentionWidth(indentionForExprBlock) {

      val blockMarker = builder.mark()
      if (!exprKind()) {
        BlockStat.parse(builder)
      }

      @tailrec
      def parseRest(isBlock: Boolean): Boolean = {
        val isInside = builder.findPreviousIndent.exists(_ >= indentionForExprBlock)
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
        blockMarker.done(ScalaElementType.BLOCK)
      } else {
        blockMarker.drop()
      }

      true
    }
  }
}

object ExprInIndentionRegion extends ExprInIndentionRegion {
  override protected def exprKind: ParsingRule = Expr
}

object PostfixExprInIndentionRegion extends ExprInIndentionRegion {
  override protected def exprKind: ParsingRule = PostfixExpr
}