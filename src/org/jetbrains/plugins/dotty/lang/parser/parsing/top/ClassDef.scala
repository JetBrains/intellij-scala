package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.params.ClassParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitDef

/**
  * @author adkozlov
  */
object ClassDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassDef with TraitDef {
  override protected val classParamClauses = ClassParamClauses
  override protected val templateOpt = TemplateOpt
  override protected val annotation = Annotation
  override protected val constrMods = ConstrMods
  override protected val typeParamClause = TypeParamClause

  override def parse(builder: ScalaPsiBuilder) = super[ClassDef].parse(builder)
}
