package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object TypeDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.TypeDcl {
  override protected def `type` = Type
}
