package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

/**
  * @author adkozlov
  */
object Def extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def {
  override protected def patDef = PatDef
  override protected def varDef = VarDef
  override protected def typeDef = TypeDef
  override protected def macroDef = MacroDef
}
