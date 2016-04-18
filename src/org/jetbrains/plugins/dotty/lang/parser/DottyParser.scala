package org.jetbrains.plugins.dotty.lang.parser

import org.jetbrains.plugins.dotty.lang.parser.parsing.Program
import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.lang.parser.ScalaParser

/**
  * @author adkozlov
  */
class DottyParser extends ScalaParser {
  override protected val blockExpr = BlockExpr
  override protected val program = Program
}
