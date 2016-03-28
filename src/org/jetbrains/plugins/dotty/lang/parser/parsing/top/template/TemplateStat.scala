package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.dotty.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}

/**
  * @author adkozlov
  */
object TemplateStat extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat {
  override protected val `def` = Def
  override protected val dcl = Dcl
  override protected val expr = Expr
  override protected val emptyDcl = EmptyDcl
}
