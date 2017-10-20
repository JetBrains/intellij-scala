package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{Block, Expr}
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.ParamClauses
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object FunDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.FunDef {
  override protected def constrExpr = ConstrExpr
  override protected def constrBlock = ConstrBlock
  override protected def `type` = Type
  override protected def block = Block
  override protected def funSig = FunSig
  override protected def expr = Expr
  override protected def paramClauses = ParamClauses
}
