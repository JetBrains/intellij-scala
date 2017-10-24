package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.params.ClassParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object TraitDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitDef with org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassDef {
  override protected def classParamClauses = ClassParamClauses
  override protected def templateOpt = TraitTemplateOpt
  override protected def annotation = Annotation
  override protected def constrMods = ConstrMods
  override protected def typeParamClause = TypeParamClause

  override def parse(builder: ScalaPsiBuilder): Boolean = super.parse(builder)
}
