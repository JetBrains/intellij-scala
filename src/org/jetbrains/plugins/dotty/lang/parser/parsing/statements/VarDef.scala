package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object VarDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.VarDef {
  override protected def patDef = PatDef
  override protected def `type` = Type
}
