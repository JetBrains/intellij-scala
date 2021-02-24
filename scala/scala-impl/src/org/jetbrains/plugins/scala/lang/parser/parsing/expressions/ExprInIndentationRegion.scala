package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.ConstrBlock

import scala.annotation.tailrec

trait ExprInIndentationRegion extends ParsingRule {
  protected def exprKind: ParsingRule
  protected def blockType: IElementType = ScCodeBlockElementType.BlockExpression

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3 || builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      return exprKind()
    }

    val indentationForExprBlock = builder.findPreviousIndent match {
      case Some(indent) => indent
      case None => return exprKind()
    }

    val blockMarker = builder.mark()
    builder.withIndentationWidth(indentationForExprBlock) {

      blockMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_WS_AND_COMMENT_TOKENS, null)
      if (!exprKind()) {
        BlockStat()
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
          } else if (!ResultExpr() && !BlockStat()) {
            builder.advanceLexer() // ate something
          }
          parseRest(isBlock = true)
        } else {
          isBlock
        }
      }

      if (parseRest(isBlock = false)) {
        blockMarker.done(blockType)
      } else {
        blockMarker.drop()
      }
    }

    true
  }
}

object ExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprKind: ParsingRule = Expr
}

object PostfixExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprKind: ParsingRule = PostfixExpr
}

object ConstrExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprKind: ParsingRule = new ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
      if (builder.getTokenType == ScalaTokenTypes.tLBRACE) ConstrBlock()
      else {
        val marker = builder.mark()
        // We expect a self invocation as first statement/sole expression,
        // but if there is no self invocation,
        // don't fail and just parse an expression.
        // This will make the following parse
        //
        //   def this() = ???
        if (SelfInvocation() || Expr()) {
          marker.done(ScalaElementType.CONSTR_EXPR)
          true
        } else {
          marker.drop()
          false
        }
      }
    }
  }
  override protected def blockType: IElementType = ScalaElementType.CONSTR_BLOCK
}