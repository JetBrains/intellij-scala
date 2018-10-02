package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

/**
  * @author adkozlov
  */
object Dcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.Dcl {
  override protected def funDcl = FunDcl
  override protected def typeDcl = TypeDcl
  override protected def varDcl = VarDcl
  override protected def valDcl = ValDcl
}
