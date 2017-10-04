package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.CaseClauses

/**
  * @author adkozlov
  */
object BlockExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr {
  override protected def block = Block
  override protected def caseClauses = CaseClauses
}
