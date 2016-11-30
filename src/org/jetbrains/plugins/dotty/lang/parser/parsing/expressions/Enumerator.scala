package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.{Guard, Pattern1}

/**
  * @author adkozlov
  */
object Enumerator extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Enumerator {
  override protected def expr = Expr
  override protected def generator = Generator
  override protected def guard = Guard
  override protected def pattern1 = Pattern1
}
