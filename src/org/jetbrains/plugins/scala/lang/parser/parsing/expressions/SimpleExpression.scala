package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

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
  val FIRST = BNF.tLITERALS

  def parse(builder : PsiBuilder) : Boolean = {

    if (BNF.tLITERALS.contains(builder.getTokenType)) {
      Literal parse (builder) // Ate literal
      subParse(builder)
    } else {
      builder.error("Wrong expression!")
      false
    }
  }

  def subParse(builder : PsiBuilder) : Boolean = {
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
            subParse(builder)
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

}