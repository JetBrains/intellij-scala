package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Block

/**
  * @author adkozlov
  */
object CaseClause extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClause {
  override protected val block = Block
  override protected val pattern = Pattern
  override protected val guard = Guard
}
