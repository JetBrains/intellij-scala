package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object VarDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.VarDcl {
  override protected val `type` = Type
}
