package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

/**
  * @author adkozlov
  */
object Expr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr {
  override protected def expr1 = Expr1
  override protected def bindings = Bindings
}
