package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Guard

/**
  * @author adkozlov
  */
object Enumerators extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Enumerators {
  override protected def generator = Generator
  override protected def guard = Guard
  override protected def enumerator = Enumerator
}
