package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

/**
  * @author adkozlov
  */
object ArgumentExprs extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs {
  override protected def blockExpr = BlockExpr
  override protected def expr = Expr
}
