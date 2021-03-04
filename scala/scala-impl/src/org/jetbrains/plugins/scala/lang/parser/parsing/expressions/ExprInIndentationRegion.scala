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

sealed trait ExprInIndentationRegion extends ParsingRule {
  protected def exprParser: ParsingRule
  protected def exprPartParser: ParsingRule = exprParser
  protected def blockType: IElementType = ScCodeBlockElementType.BlockExpression

  private final val isFollowSetIfIndented = Set(
    ScalaTokenTypes.tRPARENTHESIS,
    ScalaTokenTypes.tRBRACE,
    ScalaTokenTypes.kELSE,
    ScalaTokenTypes.kCATCH,
    ScalaTokenTypes.kFINALLY,
  )

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3) {
      return exprParser()
    }
    if (builder.getTokenType == ScalaTokenTypes.tLBRACE) {
      return exprParser()
    }

    val indentationForExprBlock = builder.findPreviousIndent match {
      case Some(indent) => indent
      case None =>
        return exprParser()
    }

    val blockMarker = builder.mark()
    builder.withIndentationWidth(indentationForExprBlock) {

      blockMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_WS_AND_COMMENT_TOKENS, null)
      if (!exprPartParser()) {
        BlockStat()
      }

      @tailrec
      def parseRest(isBlock: Boolean): Boolean = {
        val isInside = builder.findPreviousIndent.exists(_ >= indentationForExprBlock)
        if (isInside) {
          val tt = builder.getTokenType
          if (tt == ScalaTokenTypes.tSEMICOLON) {
            builder.advanceLexer() // ate ;
            parseRest(isBlock = true)
          } else if (builder.eof() || isFollowSetIfIndented(builder.getTokenType)) {
            isBlock
          } else if (!ResultExpr() && !BlockStat()) {
            builder.advanceLexer() // ate something
            parseRest(isBlock = true)
          } else {
            parseRest(isBlock = true)
          }
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
  override protected def exprParser: ParsingRule = Expr
}

object PostfixExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprParser: ParsingRule = PostfixExpr
}

object ConstrExprInIndentationRegion extends ExprInIndentationRegion {
  override protected def exprParser: ParsingRule = ConstBlockExpr
  override protected def exprPartParser: ParsingRule = new ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean =
      ConstBlockExpr.parseFirstConstrBlockExpr()
  }
  override protected def blockType: IElementType = ScalaElementType.CONSTR_BLOCK_EXPR
}


private object ConstBlockExpr extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (builder.getTokenType == ScalaTokenTypes.tLBRACE)
      ConstrBlock()
    else
      parseFirstConstrBlockExpr()
  }

  // We expect a self invocation as first statement/sole expression,
  // but if there is no self invocation,
  // don't fail and just parse an expression.
  // This will make the following parse
  //
  //   def this() = ???
  def parseFirstConstrBlockExpr()(implicit builder: ScalaPsiBuilder): Boolean =
    SelfInvocation() || Expr()
}