package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.RefinedType

/**
  * @author adkozlov
  */
object ResultExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ResultExpr {
  override protected val block = Block
  override protected val `type` = RefinedType
  override protected val bindings = Bindings
}
