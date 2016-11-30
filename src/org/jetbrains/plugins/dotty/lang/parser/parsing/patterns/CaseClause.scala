package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Block

/**
  * @author adkozlov
  */
object CaseClause extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClause {
  override protected def block = Block
  override protected def pattern = Pattern
  override protected def guard = Guard
}
