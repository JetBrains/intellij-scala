package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.RefinedType

/**
  * @author adkozlov
  */
object ResultExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ResultExpr {
  override protected def block = Block
  override protected def `type` = RefinedType
  override protected def bindings = Bindings
}
