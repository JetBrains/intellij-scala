package org.jetbrains.plugins.dotty.lang.parser.parsing

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.Pattern

/**
  * @author adkozlov
  */
object CommonUtils extends org.jetbrains.plugins.scala.lang.parser.parsing.CommonUtils {
  override protected val blockExpr = BlockExpr
  override protected val pattern = Pattern
}
