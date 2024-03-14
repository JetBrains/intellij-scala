package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType, ScalaTokenBinders}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Extension
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{IndentationRegion, ScalaPsiBuilder}
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
    ScalaTokenTypes.tCOMMA,
  )

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3 || !builder.isScala3IndentationBasedSyntaxEnabled) {
      return parseSingleExpr()
    }

    val prevIndent = builder.findPrecedingIndentation
    val indentationForExprBlock = prevIndent match {
      case Some(indent) => indent
      case None =>
        return parseSingleExpr() // expression doesn't start from a new line, parse single expression
    }

    if (!builder.isIndent(indentationForExprBlock)) {
      if (builder.isOutdentHere) {
        builder.ignoreOutdent()
        // hack! we have an outdent here, but at least *try* to parse an expression
        // Let's not do that if the next token is `end` as we can expect that it is not part of the current expression
        if (builder.getTokenText == "end") {
          return false
        }

        val errorMarker = builder.mark()
        errorMarker.error(ScalaBundle.message("line.is.indented.too.far.to.the.left"))
        val parsed = exprParser()
        if (!parsed) {
          // we do not want to show the error if we do not have valid expression,
          // e.g. in `class A {\n  def foo = \n}`
          errorMarker.drop()
        }
        return parsed // expression is unindented, parse single expression
      } else {
        return parseSingleExpr() // expression is not indented, parse a single expression
      }
    }

    val blockMarker = builder.mark()
    builder.withIndentationRegion(builder.newBracelessIndentationRegionHere) {
      builder.withEnabledNewlines {
        blockMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_WS_AND_COMMENT_TOKENS, null)

        def doParseExpr(): Boolean = {
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
            if (!builder.isOutdentHere) {
              val tt = builder.getTokenType
              if (tt == ScalaTokenTypes.tSEMICOLON) {
                builder.advanceLexer() // ate ;
                parseRest(isBlock = true)
              } else if (builder.eof() || isFollowSetIfIndented(builder.getTokenType)) {
                isBlock
              } else if (!ResultExpr(stopOnOutdent = true) && !BlockStat()) {
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

        /*
         * Special case check for case clauses in an indented region, which can be used to define partial functions
         * without braces.
         *
         * The Scala 3 syntax grammar for this is:
         * BlockExpr ::= <<< (CaseClauses | Block) >>>
         *
         * where the `<<< ts >>>` notation denotes a token sequence `ts` that is either enclosed in braces
         * `{ ts }` or that constitutes an indented region `indent ts outdent`.
         *
         * Here, only the `indent ts outdent` part is handled because the braced version is currently parsed
         * by the `BlockExpr` parsing rule.
         *
         * For now, this indentation based rule cannot be unified with `BlockExpr` as it is currently implemented,
         * because `BlockExpr` is always guarded by a token check that looks for an opening brace `{` token, and if a
         * `{` is missing, `BlockExpr` is not even attempted.
         *
         * On the other hand, general expressions in indented regions are already handled by this class.
         *
         * The implementation logic is analogous to the one in `BlockExpr`, with the difference that case clauses in
         * an indented region require that only case clauses be present, with an outdent immediately following the end
         * of the block. If this is not the case, parsing is given up and regular expression parsing is attempted
         * instead.
         */

        builder.getTokenType match {
          case ScalaTokenTypes.`kCASE` =>
            val backMarker = builder.mark()
            builder.advanceLexer() // case consumed
            builder.getTokenType match { // peek the following token
              case ScalaTokenType.ClassKeyword | ScalaTokenType.ObjectKeyword =>
                // `case class` or `case object` definition, do regular expression parsing
                backMarker.rollbackTo()
                doParseExpr()
              case _ =>
                // attempt case clauses parsing
                backMarker.rollbackTo()
                if (CaseClausesInIndentationRegion() && builder.isOutdentHere) {
                  // case clauses parsed and an outdent or eof is following, succeed with creating a block expression
                  blockMarker.done(ScCodeBlockElementType.BlockExpression)
                  true
                } else {
                  // do regular expression parsing
                  doParseExpr()
                }
            }
          case _ =>
            // do regular expression parsing
            doParseExpr()
        }
      }
    }
  }

  private def parseSingleExpr()(implicit builder: ScalaPsiBuilder): Boolean =
    builder.withIndentationRegion(builder.newExpressionRegionHere) {
      exprParser()
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
  override protected val exprPartParser: ParsingRule = new ParsingRule {
    override def parse(implicit builder: ScalaPsiBuilder): Boolean =
      ConstBlockExpr.parseFirstConstrBlockExpr()
  }
  override protected def blockType: IElementType = ScalaElementType.CONSTR_BLOCK_EXPR
}


private object ConstBlockExpr extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
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