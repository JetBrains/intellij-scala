package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef

/**
  * @author adkozlov
  */
object Def extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def {
  override protected val funDef = FunDef
  override protected val patDef = PatDef
  override protected val annotation = Annotation
  override protected val varDef = VarDef
  override protected val typeDef = TypeDef
  override protected val tmplDef = TmplDef
  override protected val macroDef = MacroDef
}
