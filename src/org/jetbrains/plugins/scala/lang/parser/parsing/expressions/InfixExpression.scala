package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PrefixExpression
import org.jetbrains.plugins.scala.lang.parser.util._
       
object InfixExpression{

/*
Default grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

Realized grammar:
InfixExpr ::= PrefixExpr
          | InfixExpr id [NewLine] PrefixExpr

***********************************************

FIRST(InfixExpression) =  ScalaTokenTypes.tIDENTIFIER
     union                PrefixExpression.FIRST

*/

  val FIRST = PrefixExpression.FIRST 

  def parse(builder : PsiBuilder) : Boolean = {
    val marker = builder.mark()
    var result = PrefixExpression parse (builder)
    if (result) {
      result = subParse (builder)
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
    else {
      builder.error("Wrong infix expression!")
      marker.done(ScalaElementTypes.INFIX_EXPR)
      result
    }
  }

  def subParse(builder : PsiBuilder) : Boolean = {

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {

        val idMarker = builder.mark()
        builder.advanceLexer()
        idMarker.done(ScalaElementTypes.IDENTIFIER)

        ParserUtils.rollForward(builder)
        if (PrefixExpression.parse(builder)) {
          subParse(builder)
        } else {
          builder.error("Wrong infix expression!")
          false
        }
      }
      case _ => true
    }
  }  

}