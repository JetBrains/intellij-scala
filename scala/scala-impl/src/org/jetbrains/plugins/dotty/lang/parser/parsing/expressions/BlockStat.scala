package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.statements.{Dcl, Def, EmptyDcl}
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef

/**
  * @author adkozlov
  */
object BlockStat extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat {
  override protected def `def` = Def
  override protected def expr1 = Expr1
  override protected def dcl = Dcl
  override protected def emptyDcl = EmptyDcl
  override protected def tmplDef = TmplDef
}
