package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef

/**
  * @author adkozlov
  */
object BlockStat extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat {
  override protected val `def` = Def
  override protected val expr1 = Expr1
  override protected val dcl = Dcl
  override protected val emptyDcl = EmptyDcl
  override protected val tmplDef = TmplDef
}
