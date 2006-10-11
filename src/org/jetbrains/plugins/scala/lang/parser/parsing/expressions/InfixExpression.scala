package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PrefixExpression
import org.jetbrains.plugins.scala.lang.parser.util._
       
object InfixExpression{

/*
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr   ::= PrefixExpr InfixExpr1

-----------------------------------------------

InfixExpr1  ::= id [NewLine] PrefixExpr InfixExpr1
               | Epsilon

***********************************************

FIRST(InfixExpression) =  ScalaTokenTypes.tIDENTIFIER
     union                PrefixExpression.FIRST

*/

  val FIRST = TokenSet.orSet(
      Array(
        PrefixExpression.FIRST,
        TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))
      )
    )

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    if (PrefixExpression.FIRST.contains(builder getTokenType)){
      PrefixExpression parse (builder)
      new InfixExpr1 parse (builder)
    } else builder.error("Wrong infix expression!")

    marker.done(ScalaElementTypes.INFIX_EXPR)
  }

  class InfixExpr1{

    def parse(builder : PsiBuilder) : Unit = {

      /*
      var flag = true
      def rollForward : Unit = {
        while ( !builder.eof() && flag){
           builder.getTokenType match{
             case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => builder.advanceLexer
             case _ => flag = false
           }
        }
        flag = true
      }
      */

      builder.getTokenType() match {
        case ScalaTokenTypes.tIDENTIFIER => {
            val idMarker = builder.mark()
            builder.advanceLexer()
            idMarker.done(ScalaElementTypes.IDENTIFIER)
            ParserUtils.rollForward(builder)
            if (PrefixExpression.FIRST.contains(builder.getTokenType())){
              PrefixExpression parse (builder)
              new InfixExpr1 parse (builder)                            
            } else builder.error("Wrong inner infix expression!")
        }
        case _ =>
      }
    }
  }



}