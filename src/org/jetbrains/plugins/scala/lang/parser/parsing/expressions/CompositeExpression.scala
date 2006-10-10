package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

object CompositeExpression{

/*
Expr1 ::= if ‘(’ Expr1 ‘)’ [NewLine] Expr [[‘;’] else Expr]
          | try ‘{’ Block ‘}’ [catch ‘{’ CaseClauses ‘}’]
          [finally Expr]
          | while ‘(’ Expr ‘)’ [NewLine] Expr
          | do Expr [StatementSeparator] while ‘(’ Expr ’)’
          | for (‘(’ Enumerators ‘)’ | ‘{’ Enumerators ‘}’)
          [NewLine] [yield] Expr
          | throw Expr
          | return [Expr]
          | [SimpleExpr ‘.’] id ‘=’ Expr
          | SimpleExpr ArgumentExprs ‘=’ Expr
          | PostfixExpr [‘:’ Type1]
          | PostfixExpr match ‘{’ CaseClauses ‘}’
          | MethodClosure
*/

  def parse(builder : PsiBuilder) : Unit = {

    //val marker = builder.mark()

    


  }


}