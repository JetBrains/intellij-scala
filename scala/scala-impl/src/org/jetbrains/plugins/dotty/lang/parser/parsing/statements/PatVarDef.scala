package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */
object PatVarDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.PatVarDef {
  override protected def patDef = PatDef
  override protected def annotation = Annotation
  override protected def varDef = VarDef
}
