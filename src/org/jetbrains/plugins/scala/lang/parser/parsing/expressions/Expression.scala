package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util._

object Expression{

/*
SIMPLE EXPRESSION
Default grammar:
SimpleExpr ::= Literal
              | Path
              | ‘(’ [Expr] ‘)’
              | BlockExpr
              | new Template
              | SimpleExpr ‘.’ id
              | SimpleExpr TypeArgs
              | SimpleExpr ArgumentExprs
              | XmlExpr

*******************************************

Realized grammar:
SimpleExpr ::= Literal
              | SimpleExpr ‘.’ id

*******************************************

FIRST(SimpleExpr) = ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN

*/
  val SIMPLE_FIRST = BNF.tLITERALS

  def parseSimpleExpr(builder : PsiBuilder) : Boolean = {

    if (BNF.tLITERALS.contains(builder.getTokenType)) {
      Literal parse (builder) // Ate literal
      subParseSimpleExpr(builder)
    } else {
      builder.error("Wrong expression!")
      false
    }
  }

  def subParseSimpleExpr(builder : PsiBuilder) : Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => {
        val dotMarker = builder.mark()
        builder.advanceLexer()
        dotMarker.done(ScalaElementTypes.DOT)
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementTypes.IDENTIFIER)
            subParseSimpleExpr(builder)
          }
          case _ => {
            builder.error("Identifier expected")
            false
          }
        }
      }
      case _ => true
    }
  }

/*
PREFIX EXPRESSION
Default grammar
PrefixExpr ::= [ ‘-’ | ‘+’ | ‘~’ | ‘!’ ] SimpleExpr

***********************************************

Realized grammar:
PrefixExpr ::= [ ‘-’ | ‘+’ | ‘~’ | ‘!’ ] SimpleExpr

*******************************************

FIRST(PrefixExpression) = ScalaTokenTypes.tPLUS
                          ScalaTokenTypes.tMINUS
                          ScalaTokenTypes.tTILDA
                          ScalaTokenTypes.tNOT
     union                SimpleExpression.FIRST
*/

  val PREFIX_FIRST = TokenSet.orSet(Array(SIMPLE_FIRST, BNF.tPREFIXES ))

  def parsePrefixExpr(builder : PsiBuilder) : Boolean = {

    val marker = builder.mark()
    var result = false

    builder getTokenType match {
      case ScalaTokenTypes.tPLUS
           | ScalaTokenTypes.tMINUS
           | ScalaTokenTypes.tTILDA
           | ScalaTokenTypes.tNOT => {
             val prefixMarker = builder.mark()
             builder.advanceLexer()
             prefixMarker.done(ScalaElementTypes.PREFIX)
             if (SIMPLE_FIRST.contains(builder.getTokenType)) {
               result = parseSimpleExpr (builder)
             } else {
              builder.error("Wrong prefix expression!")
              result = false
             }
           }
      case _ => result = parseSimpleExpr (builder)
    }
    marker.done(ScalaElementTypes.PREFIX_EXPR)
    result
  }

/*
INFIX EXPRESSION
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

FIRST(InfixExpression) =  PrefixExpression.FIRST

*/
  val INFIX_FIRST = PREFIX_FIRST

  def parseInfixExpr(builder : PsiBuilder) : Boolean = {
    val marker = builder.mark()
    var result = parsePrefixExpr(builder)

    def subParseInfixExpr() : Boolean = {
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          val rollbackMarker = builder.mark() //for rollback
          val idMarker = builder.mark()
          builder.advanceLexer()
          idMarker.done(ScalaElementTypes.IDENTIFIER)
          ParserUtils.rollForward(builder)
          if (parsePrefixExpr(builder)) {
            rollbackMarker.drop()
            subParseInfixExpr()
          } else {
            rollbackMarker.rollbackTo()
            true
          }
        }
        case _ => true
      }
    }

    if (result) {
      result = subParseInfixExpr ()
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
    else {
      builder.error("Wrong infix expression!")
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
  }

/*
POSTFIX EXPRESSION
Default grammar:
PostfixExpr ::= InfixExpr [id [NewLine]]

***********************************************

Realized grammar:                                             
PostfixExpr ::= InfixExpr [id [NewLine]]

***********************************************

FIRST(PostfixExpression) =  InffixExpression.FIRST
*/

  val POSTFIX_FIRST = INFIX_FIRST

  def parsePostfixExpr(builder : PsiBuilder) : Boolean = {
    val marker = builder.mark()
    var result = parseInfixExpr(builder)
    if (result) {
      builder.getTokenType match {
        case ScalaTokenTypes.tIDENTIFIER => {
          val idMarker = builder.mark()
          builder.advanceLexer()
          idMarker.done(ScalaElementTypes.IDENTIFIER)
          ParserUtils.rollForward(builder)
        }
        case _ =>
      }
      marker.done(ScalaElementTypes.POSTFIX_EXPR)
      result
    }
    else {
      builder.error("Wrong postfix expression!")
      marker.done(ScalaElementTypes.POSTFIX_EXPR)
      result
    }
  }

  }
 /*
  Exprs ::= Expr {‘,’ Expr}
*/
  object Exprs {
    def parse(builder: PsiBuilder): Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.tINTEGER
           | ScalaTokenTypes.tFLOAT
           | ScalaTokenTypes.kTRUE
           | ScalaTokenTypes.kFALSE
           | ScalaTokenTypes.tCHAR
           | ScalaTokenTypes.kNULL
           | ScalaTokenTypes.tSTRING_BEGIN
           | ScalaTokenTypes.tPLUS
           | ScalaTokenTypes.tMINUS
           | ScalaTokenTypes.tTILDA
           | ScalaTokenTypes.tNOT
           | ScalaTokenTypes.tIDENTIFIER
           => {
           val exprMarker = builder.mark()

           //todo: expr
           //Expr.parse(builder)
           exprMarker.done(ScalaElementTypes.EXPRESSION)

           while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
             val commaMarker = builder.mark()
             builder.advanceLexer
             commaMarker.done(ScalaElementTypes.COMMA)

           //todo: add first(expression)
             builder.getTokenType() match {
               case ScalaTokenTypes.tINTEGER
                  | ScalaTokenTypes.tFLOAT
                  | ScalaTokenTypes.kTRUE
                  | ScalaTokenTypes.kFALSE
                  | ScalaTokenTypes.tCHAR
                  | ScalaTokenTypes.kNULL
                  | ScalaTokenTypes.tSTRING_BEGIN
                  | ScalaTokenTypes.tPLUS
                  | ScalaTokenTypes.tMINUS
                  | ScalaTokenTypes.tTILDA
                  | ScalaTokenTypes.tNOT
                  | ScalaTokenTypes.tIDENTIFIER
                  | ScalaTokenTypes.kIF
                  | ScalaTokenTypes.kTRY
                  => {
                  val exprMarker = builder.mark()

                 //todo
                  //Expr.parse(builder)
                  exprMarker.done(ScalaElementTypes.EXPRESSION)
             }

             case _ => { builder.error("expected expression") }
           }
        }

        builder.getTokenType() match {
          case ScalaTokenTypes.tCOLON
             | ScalaTokenTypes.tUNDER
             | ScalaTokenTypes.tSTAR
             => {
             //todo
            }

         case _ => {}
        }

      }

      case _ => { builder.error("expected expression") }

    }
  }
}


