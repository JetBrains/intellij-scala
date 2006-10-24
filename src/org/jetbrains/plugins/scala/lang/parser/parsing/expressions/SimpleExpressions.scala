package org.jetbrains.plugins.scala.lang.parser.parsing.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._


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
  object SimpleExpr {

  val SIMPLE_FIRST = TokenSet.orSet(Array(BNF.tSIMPLE_FIRST, BNF.tLITERALS ))

    def parse(builder : PsiBuilder) : ScalaElementType = {

      def subParse: ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)
            builder.getTokenType match {
              case ScalaTokenTypes.tIDENTIFIER => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
                subParse
              }
              case _ => {
                builder.error("Identifier expected")
                ScalaElementTypes.WRONGWAY
              }
            }
          }
          case _ => ScalaElementTypes.SIMPLE_EXPR
        }
      }

      var flag = false
      var result = Literal.parse(builder)  // Literal ?
      if (!result.equals(ScalaElementTypes.WRONGWAY)) flag = true // Yes, it is!
        else result = StableId.parse(builder) // Path ?
      if (!flag && result.equals(ScalaElementTypes.PATH)) flag = true
      // ... other cases
      if (flag) subParse
        else ScalaElementTypes.WRONGWAY
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
  object PrefixExpr {

    val PREFIX_FIRST = TokenSet.orSet(Array(SimpleExpr.SIMPLE_FIRST, BNF.tPREFIXES ))

    def parse(builder : PsiBuilder) : ScalaElementType = {

      val marker = builder.mark()
      var result = ScalaElementTypes.PREFIX_EXPR

      builder getTokenType match {
        case ScalaTokenTypes.tPLUS
             | ScalaTokenTypes.tMINUS
             | ScalaTokenTypes.tTILDA
             | ScalaTokenTypes.tNOT => {
               ParserUtils.eatElement(builder, ScalaElementTypes.PREFIX)
               if (SimpleExpr.SIMPLE_FIRST.contains(builder.getTokenType)) {
                 result = SimpleExpr  parse(builder)
               } else {
                builder.error("Wrong prefix expression!")
                result = ScalaElementTypes.WRONGWAY
               }
             }
        case _ => result = SimpleExpr parse(builder)
      }
      marker.done(ScalaElementTypes.PREFIX_EXPR)
      if (result.equals(ScalaElementTypes.SIMPLE_EXPR)) ScalaElementTypes.PREFIX_EXPR
      else result
    }
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
  object InfixExpr {

    val INFIX_FIRST = PrefixExpr.PREFIX_FIRST

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val marker = builder.mark()
      var result = PrefixExpr parse(builder)

      def subParse(currentMarker: PsiBuilder.Marker) : ScalaElementType = {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            val rollbackMarker = builder.mark() //for rollback
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
            ParserUtils.rollForward(builder)
            var res = PrefixExpr parse(builder)
            if (res.equals(ScalaElementTypes.PREFIX_EXPR)) {
              rollbackMarker.drop()
              val nextMarker = currentMarker.precede()
              currentMarker.done(ScalaElementTypes.INFIX_EXPR)
              subParse(nextMarker)
            } else {
              rollbackMarker.rollbackTo()
              currentMarker.drop()
              ScalaElementTypes.INFIX_EXPR
            }
          }
          case _ => {
            currentMarker.drop()
            ScalaElementTypes.INFIX_EXPR
          }
        }
      }

      if (result.equals(ScalaElementTypes.PREFIX_EXPR)) {
        val nextMarker = marker.precede()
        marker.done(ScalaElementTypes.INFIX_EXPR)
        result = subParse(nextMarker)
        result
      }
      else {
        builder.error("Wrong infix expression!")
        marker.done(ScalaElementTypes.INFIX_EXPR)
        result
      }
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
  object PostfixExpr {

    val POSTFIX_FIRST = InfixExpr.INFIX_FIRST

    def parse(builder : PsiBuilder) : ScalaElementType = {
      val marker = builder.mark()
      var result = InfixExpr parse(builder)
      if (result.equals(ScalaElementTypes.INFIX_EXPR)) {
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
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
      /*
        Console.println("token type : " + builder.getTokenType())
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
               commaMarker.done(ScalaTokenTypes.tCOMMA)

             //todo: add first(expression)
               Console.println("token type : " + builder.getTokenType())
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

          Console.println("token type : " + builder.getTokenType())
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

      }*/
    }
  }

}

