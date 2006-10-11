package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet

object SimpleExpression{

/*
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
SimpleExpression  ::= Literal SimpleExpr1

*******************************************

SimpleExpr1 ::= '.' id SimpleExpr1
                | Epsilon

*******************************************

FIRST(SimpleExpr) = ScalaTokenTypes.tINTEGER,
           ScalaTokenTypes.tFLOAT,
           ScalaTokenTypes.kTRUE,
           ScalaTokenTypes.kFALSE,
           ScalaTokenTypes.tCHAR,
           ScalaTokenTypes.kNULL,
           ScalaTokenTypes.tSTRING_BEGIN

*/
  val FIRST = BNF.tLITERALS

  def parse(builder : PsiBuilder) : Unit = {

    //val marker = builder.mark()

    if (BNF.tLITERALS.contains(builder.getTokenType)) {
      Literal parse (builder)
      new SimpleExpr1 parse (builder)
    } else builder.error("Wrong expression!")

    //marker done (ScalaElementTypes.SIMPLE_EXPR)

  }

  class SimpleExpr1{

    def parse(builder : PsiBuilder) : Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.tDOT => {
          val dotMarker = builder.mark()
          builder.advanceLexer()
          dotMarker.done(ScalaElementTypes.DOT)
          builder.getTokenType() match {
            case ScalaTokenTypes.tIDENTIFIER => {
              val idMarker = builder.mark()
              builder.advanceLexer()
              idMarker.done(ScalaElementTypes.IDENTIFIER)
              new SimpleExpr1 parse (builder)
            }
            case _ => builder.error("Wrong expression!")
          }
        }
        case _ => 
      }
    }

  }

}