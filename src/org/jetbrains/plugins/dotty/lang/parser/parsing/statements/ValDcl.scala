package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object ValDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.ValDcl {
  override protected val `type` = Type
}
