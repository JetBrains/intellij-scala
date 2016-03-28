package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.{Type, TypeArgs}

/**
  * @author adkozlov
  */
object MacroDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.MacroDef {
  override protected val funSig = FunSig
  override protected val `type` = Type
  override protected val typeArgs = TypeArgs
}
