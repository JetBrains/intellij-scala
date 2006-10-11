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

  val FIRST = TokenSet.orSet (
      Array(
        PrefixExpression.FIRST,
        TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER))
      )
    )

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    if (PrefixExpression.FIRST.contains(builder getTokenType)){
      PrefixExpression parse (builder)
      subParse (builder)
    } else builder.error("Wrong infix expression!")

    marker.done(ScalaElementTypes.INFIX_EXPR)
  }

  def subParse(builder : PsiBuilder) : Unit = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val idMarker = builder.mark()
        builder.advanceLexer()
        idMarker.done(ScalaElementTypes.DOT)
        ParserUtils.rollForward(builder)
        PrefixExpression.parse(builder)
        subParse(builder)
      }
      case _ =>
    }
  }


}