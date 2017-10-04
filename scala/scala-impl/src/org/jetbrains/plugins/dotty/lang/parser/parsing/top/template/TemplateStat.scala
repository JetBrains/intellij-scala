package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.dotty.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}

/**
  * @author adkozlov
  */
object TemplateStat extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateStat {
  override protected def `def` = Def
  override protected def dcl = Dcl
  override protected def expr = Expr
  override protected def emptyDcl = EmptyDcl
}
