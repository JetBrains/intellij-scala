package org.jetbrains.plugins.scala.lang.dfa.cfg.transformations

import org.jetbrains.plugins.scala.lang.dfa.cfg.ScalaDfaControlFlowBuilder

class UnknownValueTransformer extends Transformable {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownValue()
}
