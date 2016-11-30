package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef

/**
  * @author adkozlov
  */
object Def extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def {
  override protected def funDef = FunDef
  override protected def patDef = PatDef
  override protected def annotation = Annotation
  override protected def varDef = VarDef
  override protected def typeDef = TypeDef
  override protected def tmplDef = TmplDef
  override protected def macroDef = MacroDef
}
