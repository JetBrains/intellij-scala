package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.BNF

object SimpleExpression{

/*
SimpleExpr ::= Literal
              | Path
              | ‘(’ [Expr] ‘)’
              | BlockExpr
              | new Template
              | SimpleExpr ‘.’ id
              | SimpleExpr TypeArgs
              | SimpleExpr ArgumentExprs
              | XmlExpr
*/

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()

    if (BNF.tLITERALS.contains(builder.getTokenType)) {
      Literal parse (builder)
    } else builder.error("Wrong expression!")

    marker.done(ScalaElementTypes.SIMPLE_EXPR)


  }


}