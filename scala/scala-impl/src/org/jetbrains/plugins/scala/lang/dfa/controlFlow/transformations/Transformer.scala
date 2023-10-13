package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder

private trait Transformer {
  val builder: ScalaDfaControlFlowBuilder
}
