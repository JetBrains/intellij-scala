package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes
import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */
object TmplDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef {
  override protected def classDef = ClassDef
  override protected def annotation = Annotation
  override protected def objectDef = ObjectDef
  override protected def traitDef = TraitDef

  override protected def elementTypes = DottyElementTypes
}
