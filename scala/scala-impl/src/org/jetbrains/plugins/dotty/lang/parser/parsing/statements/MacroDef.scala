package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.{Type, TypeArgs}

/**
  * @author adkozlov
  */
object MacroDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.MacroDef {
  override protected def funSig = FunSig
  override protected def `type` = Type
  override protected def typeArgs = TypeArgs
}
