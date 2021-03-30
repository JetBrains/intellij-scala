package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Extension
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{ConstrBlock, Def}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

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

      // We need to early parse those definitions which begin with a soft keyword
      // (extension, inline, transparent, infix, open)
      // If we don't parse them early, they will be wrongly parsed as expressions with errors.
      // For example `extension (x: String)` will be parsed as a method call
      val firstParsedAsDefWithSoftKeyword = Extension() || Def() || TmplDef()

      val firstParsedAsExpr =
        !firstParsedAsDefWithSoftKeyword && exprPartParser()

      val firstParsedAsBlockStat =
        firstParsedAsDefWithSoftKeyword || !firstParsedAsExpr && BlockStat()

      val firstParsed = firstParsedAsExpr || firstParsedAsBlockStat

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

      /**
       * If the first body element is not an expression, we also wrap it into block.
       * E.g. in such silly definition with a single variable definition: {{{
       *   def foo =
       *     var inner = 42
       * }}}
       */
      if (parseRest(isBlock = false) || firstParsedAsBlockStat) {
        blockMarker.done(blockType)
        true
      } else {
        blockMarker.drop()
        firstParsed
      }
    }
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