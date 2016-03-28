package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object VarDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.VarDef {
  override protected val patDef = PatDef
  override protected val `type` = Type
}
