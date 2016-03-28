package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Guard

/**
  * @author adkozlov
  */
object Enumerators extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Enumerators {
  override protected val generator = Generator
  override protected val guard = Guard
  override protected val enumerator = Enumerator
}
