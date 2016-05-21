package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.params.ClassParamClauses

/**
  * @author adkozlov
  */
object ClassDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassDef {
  override protected val classParamClauses = ClassParamClauses
  override protected val classTemplateOpt = ClassTemplateOpt
  override protected val annotation = Annotation
  override protected val constrMods = ConstrMods
  override protected val typeParamClause = TypeParamClause
}
