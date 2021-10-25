package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

class UnknownCallTransformer(statement: ScBlockStatement) extends Transformable {

  override def toString: String = "UnknownCallTransformer"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownCall(statement, 0)
}
