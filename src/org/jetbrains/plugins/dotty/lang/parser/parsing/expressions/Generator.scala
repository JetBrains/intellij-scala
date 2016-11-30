package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.{Guard, Pattern1}

/**
  * @author adkozlov
  */
object Generator extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Generator {
  override protected def expr = Expr
  override protected def guard = Guard
  override protected def pattern1 = Pattern1
}
