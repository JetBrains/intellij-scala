package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.{InScala3, ParserUtils}

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * Expr1 ::= ['inline'] 'if' '(' Expr ')' {nl} Expr [[semi] else Expr]
 *         | ['inline'] ‘if’  Expr ‘then’ Expr [[semi] ‘else’ Expr]
 *         | 'while' '(' Expr ')' {nl} Expr
 *         | 'try' '{' Block '}' [catch CatchHandler] ['finally' Expr ]
 *         | 'do' Expr [semi] 'while' '(' Expr ')'
 *         | 'for' ('(' Enumerators ')' | '{' Enumerators '}') {nl} ['yield'] Expr
 *         | 'throw' Expr
 * 
 *         | implicit Id => Expr  # Not in Scala Specification yet!
 *
 *         | 'return' [Expr]
 *         | [SimpleExpr '.'] id '=' Expr
 *         | SimpleExpr1 ArgumentExprs '=' Expr
 *         | PostfixExpr
 *         | PostfixExpr Ascription
 *         | PostfixExpr 'match' '{' CaseClauses '}'
 *         | InfixExpr
 *
 * CatchHandler ::= '{' CaseClauses '}'
 *                | '{' Path '}'
 *                | '(' Path ')'
 *                | Path
 */
object Expr1 extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
      //----------------------if statement------------------------//
      case ScalaTokenTypes.kIF =>
        parseIf(exprMarker)
        return true

      //--------------------while statement-----------------------//
      case ScalaTokenTypes.kWHILE =>
        val iw = builder.currentIndentationWidth
        builder.advanceLexer() //Ate while
        val parseNormal =
          if (builder.isScala3) {
            val rollbackMarker = builder.mark()
            val hasLParen = builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS
            if (ExprInIndentationRegion()) {
              if (builder.getTokenType == ScalaTokenTypes.kDO) {
                builder.advanceLexer()
                rollbackMarker.drop()
                false
              } else if (!hasLParen) {
                builder error ErrMsg("expected.do")
                rollbackMarker.drop()
                false
              } else {
                rollbackMarker.rollbackTo()
                true
              }
            } else {
              rollbackMarker.drop()
              true
            }
          } else {
            true
          }
        if (parseNormal) {
          builder.getTokenType match {
            case ScalaTokenTypes.tLPARENTHESIS =>
              builder.advanceLexer() //Ate (
              builder.disableNewlines()
              if (!Expr()) builder error ErrMsg("wrong.expression")
              builder.getTokenType match {
                case ScalaTokenTypes.tRPARENTHESIS =>
                  builder.advanceLexer() //Ate )
                case _ =>
                  builder error ErrMsg("rparenthesis.expected")
              }
              builder.restoreNewlinesState()
            case _ =>
              builder error ErrMsg("condition.expected")
          }
        }
        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }
        End(iw)
        exprMarker.done(ScalaElementType.WHILE_STMT)
        return true
      //---------------------try statement------------------------//
      case ScalaTokenTypes.kTRY =>
        val iw = builder.currentIndentationWidth
        builder.advanceLexer() //Ate try
        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }
        val catchMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kCATCH =>
            builder.advanceLexer() //Ate catch
            if (!CaseClausesOrExprCaseClause() && !ExprInIndentationRegion()) {
              builder.error(ErrMsg("wrong.expression"))
            }
            catchMarker.done(ScalaElementType.CATCH_BLOCK)
          case _ =>
            catchMarker.drop()
        }
        val finallyMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kFINALLY =>
            builder.advanceLexer() //Ate finally
            if (!ExprInIndentationRegion()) {
              builder error ErrMsg("wrong.expression")
            }
            finallyMarker.done(ScalaElementType.FINALLY_BLOCK)
          case _ =>
            finallyMarker.drop()
        }
        End(iw)
        exprMarker.done(ScalaElementType.TRY_STMT)
        return true
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO =>
        val iw = builder.currentIndentationWidth
        builder.advanceLexer() //Ate do
        if (!ExprInIndentationRegion()) builder error ErrMsg("wrong.expression")
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON =>
            builder.advanceLexer() //Ate semi
          case _ =>
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kWHILE =>
            builder.advanceLexer() //Ate while
            builder.getTokenType match {
              case ScalaTokenTypes.tLPARENTHESIS =>
                builder.advanceLexer() //Ate (
                builder.disableNewlines()
                if (!Expr()) builder error ErrMsg("wrong.expression")
                builder.getTokenType match {
                  case ScalaTokenTypes.tRPARENTHESIS =>
                    builder.advanceLexer() //Ate )
                  case _ =>
                    builder error ErrMsg("rparenthesis.expected")
                }
                builder.restoreNewlinesState()
              case _ if builder.isScala3 && ExprInIndentationRegion() =>
              case _ =>
                builder error ErrMsg("condition.expected")
            }
          case _ =>
            builder error ErrMsg("while.expected")
        }
        End(iw)
        exprMarker.done(ScalaElementType.DO_STMT)
        return true
      //----------------for statement------------------------//
      case ScalaTokenTypes.kFOR =>
        val iw = builder.currentIndentationWidth
        builder.advanceLexer() //Ate for
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE =>
            builder.advanceLexer() //Ate {
            builder.enableNewlines()
            def foo(): Unit = {
              if (!Enumerators()) {
                builder error ErrMsg("enumerators.expected")
              }
            }
            ParserUtils.parseLoopUntilRBrace(builder, foo _)
            builder.restoreNewlinesState()
          case ScalaTokenTypes.tLPARENTHESIS =>
            builder.advanceLexer() //Ate (
            builder.disableNewlines()
            if (!Enumerators()) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer()
              case _ => builder error ErrMsg("rparenthesis.expected")
            }
            builder.restoreNewlinesState()

          // NOTE: enumerators without brackets are not controlled by `-no-indent` flag
          // https://github.com/lampepfl/dotty/issues/12427#issuecomment-838979407
          case _ if builder.isScala3 =>
            builder.enableNewlines()
            if (!EnumeratorsInIndentationRegion()) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.restoreNewlinesState()

            builder.getTokenType match {
              case ScalaTokenTypes.kYIELD | ScalaTokenTypes.kDO =>
              case _ =>
                builder error ErrMsg("expected.do.or.yield")
            }
          case _ =>
            builder error ErrMsg("enumerators.expected")
        }

        if (builder.getTokenType == ScalaTokenTypes.kYIELD ||
            (builder.isScala3 && builder.getTokenType == ScalaTokenTypes.kDO)) {
          builder.advanceLexer() //Ate yield or do
        }

        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }

        End(iw)
        exprMarker.done(ScalaElementType.FOR_STMT)
        return true
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW =>
        builder.advanceLexer() //Ate throw
        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementType.THROW_STMT)
        return true
      //--------- higher kinded type lamdba --------//
      case InScala3(ScalaTokenTypes.tLSQBRACKET) =>
        TypeParamClause.parse(builder,
          mayHaveViewBounds = false,
          mayHaveContextBounds = false)

        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE =>
            builder.advanceLexer() // ate =>
            ExprInIndentationRegion()
          case _ =>
            builder.error(ScalaBundle.message("fun.sign.expected"))
        }
        exprMarker.done(ScalaElementType.POLY_FUNCTION_EXPR)
        return true
      //--------------implicit closure--------------//
      case ScalaTokenTypes.kIMPLICIT =>
        val ipmarker = builder.mark
        builder.advanceLexer() //Ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            val pmarker = builder.mark
            builder.advanceLexer() //Ate id
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE =>
                pmarker.done(ScalaElementType.PARAM)
                ipmarker.done(ScalaElementType.PARAM_CLAUSE)
                ipmarker.precede.done(ScalaElementType.PARAM_CLAUSES)

                builder.advanceLexer() //Ate =>
                if (!ExprInIndentationRegion()) builder error ErrMsg("wrong.expression")
                exprMarker.done(ScalaElementType.FUNCTION_EXPR)
                return true
              case _ =>
                pmarker.drop()
                ipmarker.drop()
            }
          case _ =>
            ipmarker.drop()
        }

      //---------------return statement-----------//
      case ScalaTokenTypes.kRETURN =>
        builder.advanceLexer() //Ate return
        if (!builder.newlineBeforeCurrentToken || builder.isScala3)
          ExprInIndentationRegion()
        exprMarker.done(ScalaElementType.RETURN_STMT)
        return true

      //---------other cases--------------//
      case _ =>
        if (builder.isScala3) {
          builder.tryParseSoftKeywordWithRollbackMarker(ScalaTokenType.InlineKeyword) match {
            case Some(inlineRollbackMarker) =>
              if (builder.getTokenType == ScalaTokenTypes.kIF) {
                inlineRollbackMarker.drop()
                parseIf(exprMarker)
                return true
              }

              if (PostfixExpr() && builder.getTokenType == ScalaTokenTypes.kMATCH) {
                inlineRollbackMarker.drop()
                parseMatch(exprMarker)
                return true
              }

              inlineRollbackMarker.rollbackTo()
            case _ =>
          }
        }

        if (!PostfixExpr()) {
          exprMarker.rollbackTo()
          return false
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN =>
            builder.advanceLexer() //Ate =
            if (!ExprInIndentationRegion()) {
              builder error ErrMsg("wrong.expression")
            }
            exprMarker.done(ScalaElementType.ASSIGN_STMT)
            return true
          case ScalaTokenTypes.tCOLON =>
            Ascription.parse(builder)
            exprMarker.done(ScalaElementType.TYPED_EXPR_STMT)
            return true
          case InScala3.orSource3(ScalaTokenTypes.tIDENTIFIER) if builder.getTokenText == "*" && builder.lookAhead(1) == ScalaTokenTypes.tRPARENTHESIS =>
            val seqMarker = builder.mark()
            builder.advanceLexer() // ate *
            seqMarker.done(ScalaElementType.SEQUENCE_ARG)
            exprMarker.done(ScalaElementType.TYPED_EXPR_STMT)
            return true
          case ScalaTokenTypes.kMATCH =>
            parseMatch(exprMarker)
            return true
          case _ =>
            exprMarker.drop()
            return true
        }
    }
    exprMarker.rollbackTo()
    false
  }

  private def parseIf(exprMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    val iw = builder.currentIndentationWidth
    builder.advanceLexer() //Ate if
    builder.getTokenType match {
      case InScala3(_) if parseParenlessIfCondition() =>
        // already parsed everything till after 'then'
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
        if (!Expr()) builder error ErrMsg("wrong.expression")
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
          case _ =>
            builder error ErrMsg("rparenthesis.expected")
        }
        builder.restoreNewlinesState()
      case _ =>
        builder error ErrMsg("condition.expected")
    }

    builder.skipExternalToken()

    if (!ExprInIndentationRegion()) {
      builder error ErrMsg("wrong.expression")
    }
    val rollbackMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tSEMICOLON =>
        builder.advanceLexer() //Ate semi
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kELSE =>
        builder.advanceLexer()
        if (!ExprInIndentationRegion()) builder error ErrMsg("wrong.expression")
        rollbackMarker.drop()
      case _ =>
        rollbackMarker.rollbackTo()
    }
    End(iw)
    exprMarker.done(ScalaElementType.IF_STMT)
  }

  def parseParenlessIfCondition()(implicit builder: ScalaPsiBuilder): Boolean = {
    val startedWithLParen = builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS
    val rollbackMarker = builder.mark()
    val success = ExprInIndentationRegion() && (
      if (builder.getTokenType == ScalaTokenType.ThenKeyword) {
        builder.advanceLexer()
        true
      } else if (startedWithLParen) {
        false
      } else {
        builder error ErrMsg("expected.then")
        true
      }
    )

    if (success) rollbackMarker.drop()
    else rollbackMarker.rollbackTo()

    success
  }

  def parseMatch(exprMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    val iw = builder.currentIndentationWidth
    builder.advanceLexer() //Ate match
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()

        def foo(): Unit = {
          if (!CaseClauses()) {
            builder error ErrMsg("case.clauses.expected")
          }
        }

        ParserUtils.parseLoopUntilRBrace(builder, foo _)
        builder.restoreNewlinesState()

      case InScala3(ScalaTokenTypes.kCASE) if builder.isScala3IndentationBasedSyntaxEnabled =>
        CaseClausesInIndentationRegion()

      case _ => builder error ErrMsg("case.clauses.expected")
    }

    End(iw)
    exprMarker.done(ScalaElementType.MATCH_STMT)
  }
}