package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
//import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 03.03.2008
* Time: 16:08:46
* To change this template use File | Settings | File Templates.
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
            if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression",new Array[Object](0))
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
              }
            }
          }
          case _ => {
            builder error ScalaBundle.message("condition.expected", new Array[Object](0))
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate nl
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) {
          builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
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
            if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
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
            if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression",new Array[Object](0))
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
              }
            }
          }
          case _ => {
            builder error ScalaBundle.message("condition.expected", new Array[Object](0))
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            builder.advanceLexer //Ate nl
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) {
          builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
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
            if (!Block.parse(builder)) {
              builder error ScalaBundle.message("block.expected", new Array[Object](0))
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => {
                builder.advanceLexer //Ate }
              }
              case _ => {
                builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
              }
            }
          }
          case _ => {
            builder error ScalaBundle.message("block.expected", new Array[Object](0))
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
                  builder error ScalaBundle.message("case.clauses.expected",new Array[Object](0))
                }
                builder.getTokenType match {
                  case ScalaTokenTypes.tRBRACE => {
                    builder.advanceLexer //Ate }
                  }
                  case _ => {
                    builder error ScalaBundle.message("rbrace.expected",new Array[Object](0))
                  }
                }
              }
              case _ => {
                builder error ScalaBundle.message("case.clauses.expected", new Array[Object](0))
              }
            }
            catchMarker.done(ScalaElementTypes.CATCH_BLOCK)
            val finallyMarker = builder.mark
            builder.getTokenType match {
              case ScalaTokenTypes.kFINALLY => {
                builder.advanceLexer//Ate finally
                if (!Expr.parse(builder)) {
                  builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
                }
                finallyMarker.done(ScalaElementTypes.FINALLY_BLOCK)
              }
              case _ => {
                finallyMarker.drop
              }
            }
          }
          case _ => {
            catchMarker.drop
          }
        }
        exprMarker.done(ScalaElementTypes.TRY_STMT)
        return true
      }
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO => {
        builder.advanceLexer //Ate do
        if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
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
                if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression",new Array[Object](0))
                builder.getTokenType match {
                  case ScalaTokenTypes.tRPARENTHESIS => {
                    builder.advanceLexer //Ate )
                  }
                  case _ => {
                    builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
                  }
                }
              }
              case _ => {
                builder error ScalaBundle.message("condition.expected", new Array[Object](0))
              }
            }
          }
          case _ => {
            builder error ScalaBundle.message("while.expected", new Array[Object](0))
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
              builder error ScalaBundle.message("enumerators.expected", new Array[Object](0))
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRBRACE => builder.advanceLexer
              case _ => builder error ScalaBundle.message("rbrace.expected", new Array[Object](0))
            }
          }
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            if (!Enumerators.parse(builder)) {
              builder error ScalaBundle.message("enumerators.expected", new Array[Object](0))
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer
              case _ => builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
            }
          }
          case _ => {
            builder error ScalaBundle.message("enumerators.expected", new Array[Object](0))
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            if (LineTerminator(builder.getTokenText)) builder.advanceLexer
            else {
              builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
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
        if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
        exprMarker.done(ScalaElementTypes.FOR_STMT)
        return true
      }
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW => {
        builder.advanceLexer //Ate throw
        if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
        exprMarker.done(ScalaElementTypes.THROW_STMT)
        return true
      }
      //---------------return statement-----------//
      case ScalaTokenTypes.kRETURN => {
        builder.advanceLexer //Ate return
        Expr parse builder
        exprMarker.done(ScalaElementTypes.RETURN_STMT)
      }
      //---------other cases--------------//
      case _ => {
        val rollbackMarker = builder.mark
        if (SimpleExpr.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tASSIGN => {
              builder.advanceLexer //Ate =
              if (!Expr.parse(builder)) builder error ScalaBundle.message("wrong.expression", new Array[Object](0))
              rollbackMarker.drop
              exprMarker.done(ScalaElementTypes.ASSIGN_STMT)
              return true
            }
            case _ => {
              rollbackMarker.rollbackTo
            }
          }
        }
        if (!PostfixExpr.parse(builder)) {
          exprMarker.rollbackTo
          return false
        }
        builder.getTokenType match {
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
                  builder error ScalaBundle.message("case.clauses.expected",new Array[Object](0))
                }
                builder.getTokenType match {
                  case ScalaTokenTypes.tRBRACE => {
                    builder.advanceLexer //Ate }
                  }
                  case _ => {
                    builder error ScalaBundle.message("rbrace.expected",new Array[Object](0))
                  }
                }
              }
              case _ => {
                builder error ScalaBundle.message("case.clauses.expected", new Array[Object](0))
              }
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