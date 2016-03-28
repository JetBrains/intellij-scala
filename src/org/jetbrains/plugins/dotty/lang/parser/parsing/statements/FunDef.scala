package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{Block, Expr}
import org.jetbrains.plugins.dotty.lang.parser.parsing.params.ParamClauses
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object FunDef extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.FunDef {
  override protected val constrExpr = ConstrExpr
  override protected val constrBlock = ConstrBlock
  override protected val `type` = Type
  override protected val block = Block
  override protected val funSig = FunSig
  override protected val expr = Expr
  override protected val paramClauses = ParamClauses
}
