package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.params.ClassParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object TraitDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitDef with org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassDef {
  override protected val classParamClauses = ClassParamClauses
  override protected val templateOpt = TraitTemplateOpt
  override protected val annotation = Annotation
  override protected val constrMods = ConstrMods
  override protected val typeParamClause = TypeParamClause

  override def parse(builder: ScalaPsiBuilder): Boolean = super.parse(builder)
}
