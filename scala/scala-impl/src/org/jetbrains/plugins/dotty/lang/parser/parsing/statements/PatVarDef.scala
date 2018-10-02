package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

/**
  * @author adkozlov
  */
object PatVarDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.PatVarDef {
  override protected def patDef = PatDef
  override protected def varDef = VarDef
}
