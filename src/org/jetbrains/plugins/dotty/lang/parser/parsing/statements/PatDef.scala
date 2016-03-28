package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.dotty.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object PatDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.PatDef {
  override protected val expr = Expr
  override protected val `type` = Type
  override protected val pattern2 = Pattern2
}
