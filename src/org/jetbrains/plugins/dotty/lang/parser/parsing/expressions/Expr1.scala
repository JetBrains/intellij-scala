package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.CaseClauses

/**
  * @author adkozlov
  */
object Expr1 extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr1 {
  override protected val block = Block
  override protected val caseClauses = CaseClauses
  override protected val postfixExpr = PostfixExpr
  override protected val expr = Expr
  override protected val enumerators = Enumerators
  override protected val ascription = Ascription
}
