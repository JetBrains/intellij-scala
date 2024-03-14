package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.{InScala3, ParserUtils}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}

import scala.annotation.tailrec

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
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark()
    builder.getTokenType match {
      //----------------------if statement------------------------//
      case ScalaTokenTypes.kIF =>
        parseIf(exprMarker)
        return true

      //--------------------while statement-----------------------//
      case ScalaTokenTypes.kWHILE =>
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
              if (!Expr()) builder.wrongExpressionError()
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
          builder.wrongExpressionError()
        }
        End()
        exprMarker.done(ScalaElementType.WHILE_STMT)
        return true
      //---------------------try statement------------------------//
      case ScalaTokenTypes.kTRY =>
        builder.advanceLexer() //Ate try
        if (!ExprInIndentationRegion()) {
          builder.wrongExpressionError()
        }
        val catchMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.kCATCH =>
            builder.advanceLexer() //Ate catch
            if (!CaseClausesOrExprCaseClause() && !ExprInIndentationRegion()) {
              builder.wrongExpressionError()
            }
            catchMarker.done(ScalaElementType.CATCH_BLOCK)
          case _ =>
            catchMarker.drop()
        }
        val finallyMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.kFINALLY =>
            builder.advanceLexer() //Ate finally
            if (!ExprInIndentationRegion()) {
              builder.wrongExpressionError()
            }
            finallyMarker.done(ScalaElementType.FINALLY_BLOCK)
          case _ =>
            finallyMarker.drop()
        }
        End()
        exprMarker.done(ScalaElementType.TRY_STMT)
        return true
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO =>
        builder.advanceLexer() //Ate do
        if (!ExprInIndentationRegion()) builder.wrongExpressionError()
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
                if (!Expr()) builder.wrongExpressionError()
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
        End()
        exprMarker.done(ScalaElementType.DO_STMT)
        return true
      //----------------for statement------------------------//
      case ScalaTokenTypes.kFOR =>
        builder.advanceLexer() //Ate for
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE =>
            builder.advanceLexer() //Ate {
            builder.enableNewlines()
            builder.withIndentationRegion(builder.newBracedIndentationRegionHere) {
              ParserUtils.parseLoopUntilRBrace() {
                if (!Enumerators()) {
                  builder error ErrMsg("enumerators.expected")
                }
              }
            }
            builder.restoreNewlinesState()
          case ScalaTokenTypes.tLPARENTHESIS =>
            //(scala3) check if '(' belongs to generator tuple pattern, examples:
            //for (value, index) <- List("a", "b", "c").zipWithIndex do println(value)
            //for (tupleValue) <- List("a", "b", "c").zipWithIndex do println(value)
            val backupMarker = builder.mark()
            val bracelessForStartingWithPatternGenerator =
              builder.isScala3 && parseScala3ForRest()

            if (bracelessForStartingWithPatternGenerator) {
              backupMarker.drop()
            }
            else {
              backupMarker.rollbackTo()
              builder.advanceLexer() //Ate (
              builder.disableNewlines()
              if (!Enumerators()) {
                builder error ErrMsg("enumerators.expected")
              }
              builder.getTokenType match {
                case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer()
                case _=> builder error ErrMsg("rparenthesis.expected")
              }
              builder.restoreNewlinesState()
            }

          // NOTE: enumerators without brackets are not controlled by `-no-indent` flag
          // https://github.com/lampepfl/dotty/issues/12427#issuecomment-838979407
          case _ if builder.isScala3 =>
            parseScala3ForRest()
          case _ =>
            builder error ErrMsg("enumerators.expected")
        }

        if (builder.getTokenType == ScalaTokenTypes.kYIELD ||
            (builder.isScala3 && builder.getTokenType == ScalaTokenTypes.kDO)) {
          builder.advanceLexer() //Ate yield or do
        }

        if (!ExprInIndentationRegion()) {
          builder.wrongExpressionError()
        }

        End()
        exprMarker.done(ScalaElementType.FOR_STMT)
        return true
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW =>
        builder.advanceLexer() //Ate throw
        if (!ExprInIndentationRegion()) {
          builder.wrongExpressionError()
        }
        exprMarker.done(ScalaElementType.THROW_STMT)
        return true
      //--------------implicit closure--------------//
      case ScalaTokenTypes.kIMPLICIT =>
        val ipmarker = builder.mark()
        builder.advanceLexer() //Ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER =>
            val pmarker = builder.mark()
            builder.advanceLexer() //Ate id
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE =>
                completeParamClauses(pmarker)(ipmarker)

                builder.advanceLexer() //Ate =>
                if (!ExprInIndentationRegion()) builder.wrongExpressionError()
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
        if (!builder.newlineBeforeCurrentToken || (builder.isScala3 && builder.isIndentHere))
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

              if (PostfixExpr() && builder.getTokenType == ScalaTokenTypes.kMATCH && !builder.isOutdentHere) {
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
              builder.wrongExpressionError()
            }
            exprMarker.done(ScalaElementType.ASSIGN_STMT)
            return true
          case ScalaTokenTypes.tCOLON =>
            Ascription()
            exprMarker.done(ScalaElementType.TYPED_EXPR_STMT)
            return true
          case ScalaTokenTypes.tIDENTIFIER if
              builder.features.`Scala 3 vararg splice syntax` &&
              builder.getTokenText == "*" &&
              builder.lookAhead(1) == ScalaTokenTypes.tRPARENTHESIS =>
            val seqMarker = builder.mark()
            builder.advanceLexer() // ate *
            seqMarker.done(ScalaElementType.SEQUENCE_ARG)
            exprMarker.done(ScalaElementType.TYPED_EXPR_STMT)
            return true
          case ScalaTokenTypes.kMATCH if !builder.isOutdentHere =>
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

  private def parseScala3ForRest()(implicit builder: ScalaPsiBuilder): Boolean = {
    var hasError = false
    builder.withEnabledNewlines {
      builder.withIndentationRegion(builder.newBracelessIndentationRegionHere) {
        if (!Enumerators()) {
          hasError = true
          builder.error(ErrMsg("enumerators.expected"))
        }
      }
    }
    if (!isDoOrYield(builder)) {
      hasError = true
      builder.error(ErrMsg("expected.do.or.yield"))
    }
    !hasError
  }

  private def isDoOrYield(builder: ScalaPsiBuilder): Boolean = {
    val tt = builder.getTokenType
    tt match {
      case ScalaTokenTypes.kYIELD | ScalaTokenTypes.kDO => true
      case _                                            => false
    }
  }

  private def parseIf(exprMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    builder.advanceLexer() //Ate if
    lazy val condIndent = builder.findPrecedingIndentation
    builder.getTokenType match {
      case InScala3(_) if condIndent.forall(builder.isIndent) && parseParenlessIfCondition(condIndent.isDefined) =>
        // already parsed everything till after 'then'
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
        if (!Expr()) builder.wrongExpressionError()
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
          case _ =>
            builder error ErrMsg("rparenthesis.expected")
        }
        builder.restoreNewlinesState()
      case _ =>
        builder error ErrMsg("condition.expected")

        if (builder.findPrecedingIndentation.exists(iw => !builder.isIndent(iw))) {
          exprMarker.done(ScalaElementType.IF_STMT)
          End()
          return
        }
    }

    builder.skipExternalToken()

    if (!ExprInIndentationRegion()) {
      builder.wrongExpressionError()
    }
    val rollbackMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tSEMICOLON =>
        builder.advanceLexer() //Ate semi
      case _ =>
    }
    builder.getTokenType match {
      case ScalaTokenTypes.kELSE if !(
          // if `else` is indented more to the left in Scala 3 indentation based syntax
          // detach else branch from the current if statement
          builder.isScala3IndentationBasedSyntaxEnabled &&
            builder.isOutdentHere
          ) =>
        builder.advanceLexer()
        if (!ExprInIndentationRegion()) builder.wrongExpressionError()
        rollbackMarker.drop()
      case _ =>
        rollbackMarker.rollbackTo()
    }
    End()
    exprMarker.done(ScalaElementType.IF_STMT)
  }

  def parseParenlessIfCondition(hasIndent: Boolean)(implicit builder: ScalaPsiBuilder): Boolean = {
    val startedWithLParen = builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS
    val rollbackMarker = builder.mark()
    val success = builder.withDisabledNewlinesIf(!hasIndent)(ExprInIndentationRegion()) && (
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

  @tailrec
  def parseMatch(exprMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit = {
    builder.advanceLexer() //Ate match
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()

        builder.withIndentationRegion(builder.newBracedIndentationRegionHere) {
          ParserUtils.parseLoopUntilRBrace() {
            if (!CaseClauses()) {
              builder error ErrMsg("case.clauses.expected")
            }
          }
        }
        builder.restoreNewlinesState()

      case InScala3(ScalaTokenTypes.kCASE) if builder.isScala3IndentationBasedSyntaxEnabled =>
        CaseClausesInIndentationRegion()

      case _ => builder error ErrMsg("case.clauses.expected")
    }

    End()

    exprMarker.done(ScalaElementType.MATCH_STMT)

    if (builder.getTokenType == ScalaTokenTypes.kMATCH && !builder.isOutdentHere) {
      parseMatch(exprMarker.precede())
    }
  }
}
