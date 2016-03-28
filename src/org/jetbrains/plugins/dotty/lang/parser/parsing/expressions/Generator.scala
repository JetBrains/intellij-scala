package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.{Guard, Pattern1}

/**
  * @author adkozlov
  */
object Generator extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Generator {
  override protected val expr = Expr
  override protected val guard = Guard
  override protected val pattern1 = Pattern1
}
