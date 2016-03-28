package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

/**
  * @author adkozlov
  */
object InfixExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr {
  override protected val prefixExpr = PrefixExpr
}
