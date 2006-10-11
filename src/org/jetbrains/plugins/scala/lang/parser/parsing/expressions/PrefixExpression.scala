package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SimpleExpression

object PrefixExpression{

/*
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

  val FIRST = TokenSet.orSet(Array(SimpleExpression.FIRST, BNF.tPREFIXES ))

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    builder getTokenType match {
      case ScalaTokenTypes.tPLUS
           | ScalaTokenTypes.tMINUS
           | ScalaTokenTypes.tTILDA
           | ScalaTokenTypes.tNOT => {
             val prefixMarker = builder.mark()
             builder.advanceLexer()
             prefixMarker.done(ScalaElementTypes.PREFIX)
             if (SimpleExpression.FIRST.contains(builder.getTokenType)) {
               SimpleExpression parse (builder)
             } else builder.error("Wrong expression!")
           }
      case _ => SimpleExpression parse (builder)
    }
    marker.done(ScalaElementTypes.PREFIX_EXPR)
  }

}