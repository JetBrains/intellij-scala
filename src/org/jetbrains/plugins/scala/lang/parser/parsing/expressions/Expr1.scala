package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import patterns.CaseClauses

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * Expr1 ::= 'if' '(' Expr ')' {nl} Expr [[semi] else Expr]
 *         | 'while' '(' Expr ')' {nl} Expr
 *         | 'try' '{' Block '}' [catch '{' CaseClauses '}'] ['finally' Expr ]
 *         | 'do' Expr [semi] 'while' '(' Expr ')'
 *         | 'for' ('(' Enumerators ')' | '{' Enumerators '}') {nl} ['yield'] Expr
 *         | 'throw' Expr
 *         | 'return' [Expr]
 *         | [SimpleExpr '.'] id '=' Expr
 *         | SimpleExpr1 ArgumentExprs '=' Expr
 *         | PostfixExpr
 *         | PostfixExpr Ascription
 *         | PostfixExpr 'match' '{' CaseClauses '}'
 */

object Expr1 {
  def parse(builder: PsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
    //----------------------if statement------------------------//
      case ScalaTokenTypes.kIF => {
        builder.advanceLexer //Ate if
        builder.getTokenType match {
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ErrMsg("rparenthesis.expected")
              }
            }
          }
          case _ => {
            builder error ErrMsg("condition.expected")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate nl
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        val rollbackMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON | ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate semi
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kELSE => {
            builder.advanceLexer
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            rollbackMarker.drop
          }
          case _ => {
            rollbackMarker.rollbackTo
          }
        }
        exprMarker.done(ScalaElementTypes.IF_STMT)
        return true
      }
      //--------------------while statement-----------------------//
      case ScalaTokenTypes.kWHILE => {
        builder.advanceLexer //Ate while
        builder.getTokenType match {
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ErrMsg("rparenthesis.expected")
              }
            }
          }
          case _ => {
            builder error ErrMsg("condition.expected")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate nl
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementTypes.WHILE_STMT)
        return true
      }
      //---------------------try statement------------------------//
      case ScalaTokenTypes.kTRY => {
        val tryMarker = builder.mark
        builder.advanceLexer //Ate try
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            builder.advanceLexer //Ate {
            if (!Block.parse(builder, false)) {
              builder error ErrMsg("block.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate }
              }
              case _ => {
                builder error ErrMsg("rbrace.expected")
              }
            }
          }
          case _ => {
            builder error ErrMsg("block.expected")
          }
        }
        tryMarker.done(ScalaElementTypes.TRY_BLOCK)
        val catchMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kCATCH => {
            builder.advanceLexer //Ate catch
            builder.getTokenType match {
              case ScalaTokenTypes.tLBRACE => {
                builder.advanceLexer //Ate }
                if (!CaseClauses.parse(builder)) {
                  builder error ErrMsg("case.clauses.expected")
                }
                builder.getTokenType match {
                  case ScalaTokenTypes.tRBRACE => {
                    builder.advanceLexer //Ate }
                  }
                  case _ => {
                    builder error ErrMsg("rbrace.expected")
                  }
                }
              }
              case _ => {
                builder error ErrMsg("case.clauses.expected")
              }
            }
            catchMarker.done(ScalaElementTypes.CATCH_BLOCK)
          }
          case _ => {
            catchMarker.drop
          }
        }
        val finallyMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kFINALLY => {
            builder.advanceLexer //Ate finally
            if (!Expr.parse(builder)) {
              builder error ErrMsg("wrong.expression")
            }
            finallyMarker.done(ScalaElementTypes.FINALLY_BLOCK)
          }
          case _ => {
            finallyMarker.drop
          }
        }
        exprMarker.done(ScalaElementTypes.TRY_STMT)
        return true
      }
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO => {
        builder.advanceLexer //Ate do
        if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON | ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate semi
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kWHILE => {
            builder.advanceLexer //Ate while
            builder.getTokenType match {
              case ScalaTokenTypes.tLPARENTHESIS => {
                builder.advanceLexer //Ate (
                if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
                builder.getTokenType match {
                  case ScalaTokenTypes.tRPARENTHESIS => {
                    builder.advanceLexer //Ate )
                  }
                  case _ => {
                    builder error ErrMsg("rparenthesis.expected")
                  }
                }
              }
              case _ => {
                builder error ErrMsg("condition.expected")
              }
            }
          }
          case _ => {
            builder error ErrMsg("while.expected")
          }
        }
        exprMarker.done(ScalaElementTypes.DO_STMT)
        return true
      }
      //----------------for statement------------------------//
      case ScalaTokenTypes.kFOR => {
        builder.advanceLexer //Ate for
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            builder.advanceLexer //Ate {
            if (!Enumerators.parse(builder)) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => builder.advanceLexer
              case _ => builder error ErrMsg("rbrace.expected")
            }
          }
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            if (!Enumerators.parse(builder)) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer
              case _ => builder error ErrMsg("rparenthesis.expected")
            }
          }
          case _ => {
            builder error ErrMsg("enumerators.expected")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) builder.advanceLexer
            else {
              builder error ErrMsg("wrong.expression")
              exprMarker.done(ScalaElementTypes.FOR_STMT)
              return true
            }
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kYIELD => {
            builder.advanceLexer //Ate yield
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
        exprMarker.done(ScalaElementTypes.FOR_STMT)
        return true
      }
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW => {
        builder.advanceLexer //Ate throw
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementTypes.THROW_STMT)
        return true
      }
      //---------------return statement-----------//
      case ScalaTokenTypes.kRETURN => {
        builder.advanceLexer //Ate return
        Expr parse builder
        exprMarker.done(ScalaElementTypes.RETURN_STMT)
        return true
      }
      //---------other cases--------------//
      case _ => {
        if (!PostfixExpr.parse(builder)) {
          exprMarker.rollbackTo
          return false
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (!Expr.parse(builder)) {
              builder error ErrMsg("wrong.expression")
            }
            exprMarker.done(ScalaElementTypes.ASSIGN_STMT)
            return true
          }
          case ScalaTokenTypes.tCOLON => {
            Ascription parse builder
            exprMarker.done(ScalaElementTypes.TYPED_EXPR_STMT)
            return true
          }
          case ScalaTokenTypes.kMATCH => {
            builder.advanceLexer //Ate match
            builder.getTokenType match {
              case ScalaTokenTypes.tLBRACE => {
                builder.advanceLexer //Ate }
                if (!CaseClauses.parse(builder)) {
                  builder error ErrMsg("case.clauses.expected")
                }
                builder.getTokenType match {
                  case ScalaTokenTypes.tRBRACE => {
                    builder.advanceLexer //Ate }
                  }
                  case _ => builder error ErrMsg("rbrace.expected")
                }
              }
              case _ => builder error ErrMsg("case.clauses.expected")
            }
            exprMarker.done(ScalaElementTypes.MATCH_STMT)
            return true
          }
          case _ => {
            exprMarker.drop
            return true
          }
        }
      }
    }
    exprMarker.rollbackTo
    return false
  }
}