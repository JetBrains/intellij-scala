package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */
object Dcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl {
  override protected val funDcl = FunDcl
  override protected val annotation = Annotation
  override protected val typeDcl = TypeDcl
  override protected val varDcl = VarDcl
  override protected val valDcl = ValDcl
}
