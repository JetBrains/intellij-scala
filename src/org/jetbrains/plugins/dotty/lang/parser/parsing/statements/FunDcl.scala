package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object FunDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.FunDcl {
  override protected val funSig = FunSig
  override protected val `type` = Type
}
