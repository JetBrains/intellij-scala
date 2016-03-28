package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.statements.PatVarDef

/**
  * @author adkozlov
  */
object EarlyDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.EarlyDef {
  override protected val patVarDef = PatVarDef
}
