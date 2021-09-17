package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder

class UnknownValueTransformer extends Transformable {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownValue()
}
