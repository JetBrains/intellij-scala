package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object FunDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.FunDcl {
  override protected def funSig = FunSig
  override protected def `type` = Type
}
