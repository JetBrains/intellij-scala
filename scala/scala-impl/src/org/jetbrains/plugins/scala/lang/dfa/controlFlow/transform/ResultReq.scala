package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.ResultReq.BuilderContext

sealed abstract class ResultReq(resultNeeded: Boolean) {
  def provideOne[R](f: => R)(implicit builderContext: BuilderContext): R = {
    val result = f
    if (!resultNeeded) {
      builderContext.builder.pop()
    }
    result
  }

  def ifResultNeeded(f: => Unit): Unit =
    if (resultNeeded) {
      f
    }
}

object ResultReq {
  case object Required extends ResultReq(true)
  case object None extends ResultReq(false)

  final class BuilderContext(private[ResultReq] val builder: ScalaDfaControlFlowBuilder) extends AnyVal
}