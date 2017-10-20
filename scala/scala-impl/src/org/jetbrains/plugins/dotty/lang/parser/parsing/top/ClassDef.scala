package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.params.ClassParamClauses

/**
  * @author adkozlov
  */
object ClassDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassDef {
  override protected def classParamClauses = ClassParamClauses
  override protected def templateOpt = ClassTemplateOpt
  override protected def annotation = Annotation
  override protected def constrMods = ConstrMods
  override protected def typeParamClause = TypeParamClause
}
