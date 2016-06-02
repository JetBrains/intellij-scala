package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes
import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */
object TmplDef extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef {
  override protected val classDef = ClassDef
  override protected val annotation = Annotation
  override protected val objectDef = ObjectDef
  override protected val traitDef = ClassDef

  override protected val elementTypes = DottyElementTypes
}
