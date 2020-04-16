package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.End
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClauses
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * Expr1 ::= 'if' '(' Expr ')' {nl} Expr [[semi] else Expr]
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
        builder.advanceLexer() //Ate if
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
          case _ if builder.isScala3 =>
            if (!Expr()) {
              builder error ErrMsg("wrong.expression")
            }
            if (builder.getTokenType == ScalaTokenType.Then) {
              builder.advanceLexer()
            } else if (!builder.isPrecededByNewIndent) {
              builder error ErrMsg("expected.then")
            }
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
        exprMarker.done(ScalaElementType.IF_STMT)
        return true
      //--------------------while statement-----------------------//
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
          case _ =>
            builder error ErrMsg("condition.expected")
        }
        if (!Expr()) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementType.WHILE_STMT)
        return true
      //---------------------try statement------------------------//
      case ScalaTokenTypes.kTRY =>
        builder.advanceLexer() //Ate try
        if (!ExprInIndentationRegion()) {
          builder error ErrMsg("wrong.expression")
        }
        val catchMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kCATCH =>
            builder.advanceLexer() //Ate catch
            if (!CaseClausesOrExprCaseClause() && !Expr()) {
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
        End()
        exprMarker.done(ScalaElementType.TRY_STMT)
        return true
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO =>
        builder.advanceLexer() //Ate do
        if (!Expr()) builder error ErrMsg("wrong.expression")
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
              case _ =>
                builder error ErrMsg("condition.expected")
            }
          case _ =>
            builder error ErrMsg("while.expected")
        }
        exprMarker.done(ScalaElementType.DO_STMT)
        return true
      //----------------for statement------------------------//
      case ScalaTokenTypes.kFOR =>
        builder.advanceLexer() //Ate for
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE =>
            builder.advanceLexer() //Ate {
            builder.enableNewlines()
            def foo(): Unit = {
              if (!Enumerators.parse(builder)) {
                builder error ErrMsg("enumerators.expected")
              }
            }
            ParserUtils.parseLoopUntilRBrace(builder, foo _)
            builder.restoreNewlinesState()
          case ScalaTokenTypes.tLPARENTHESIS =>
            builder.advanceLexer() //Ate (
            builder.disableNewlines()
            if (!Enumerators.parse(builder)) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer()
              case _ => builder error ErrMsg("rparenthesis.expected")
            }
            builder.restoreNewlinesState()
          case _ =>
            builder error ErrMsg("enumerators.expected")
        }
        if (builder.twoNewlinesBeforeCurrentToken) {
          builder.error(ErrMsg("wrong.expression"))
          exprMarker.done(ScalaElementType.FOR_STMT)
          return true
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kYIELD =>
            builder.advanceLexer() //Ate yield
            if (!ExprInIndentationRegion.parse(builder)) builder error ErrMsg("wrong.expression")
          case _ =>
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementType.FOR_STMT)
        return true
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW =>
        builder.advanceLexer() //Ate throw
        if (!Expr()) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementType.THROW_STMT)
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
                if (!Expr()) builder error ErrMsg("wrong.expression")
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
        if (!builder.newlineBeforeCurrentToken)
        ExprInIndentationRegion()
        exprMarker.done(ScalaElementType.RETURN_STMT)
        return true

      //---------other cases--------------//
      case _ =>
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
          case ScalaTokenTypes.kMATCH =>
            builder.advanceLexer() //Ate match
            builder.getTokenType match {
              case ScalaTokenTypes.tLBRACE =>
                builder.advanceLexer() //Ate {
                builder.enableNewlines()
                def foo(): Unit = {
                  if (!CaseClauses.parse(builder)) {
                    builder error ErrMsg("case.clauses.expected")
                  }
                }
                ParserUtils.parseLoopUntilRBrace(builder, foo _)
                builder.restoreNewlinesState()

              case ScalaTokenTypes.kCASE if builder.isScala3 =>
                CaseClausesInIndentationRegion()

              case _ => builder error ErrMsg("case.clauses.expected")
            }

            End()
            exprMarker.done(ScalaElementType.MATCH_STMT)
            return true
          case _ =>
            exprMarker.drop()
            return true
        }
    }
    exprMarker.rollbackTo()
    false
  }
}