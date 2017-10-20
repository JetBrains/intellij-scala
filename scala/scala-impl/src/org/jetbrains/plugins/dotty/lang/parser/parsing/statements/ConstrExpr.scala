package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.SelfInvocation

/**
  * @author adkozlov
  */
object ConstrExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.ConstrExpr {
  override protected def constrBlock = ConstrBlock
  override protected def selfInvocation = SelfInvocation
}
