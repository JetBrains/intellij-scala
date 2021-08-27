package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

class ScalaDfaControlFlowBuilder(private val body: ScExpression, private val factory: DfaValueFactory) {

  private val flow = new ControlFlow(factory, body)

  def buildFlow(): Option[ControlFlow] = {
    processExpression(body)
    Some(flow)
  }

  // TODO refactor into a different design, once a basic version works
  private def processExpression(expression: ScExpression): Unit = {
    expression match {
      case block: ScBlockExpr => processBlock(block)
      case _ => println(s"Unsupported expression: $expression")
    }
  }

  private def processBlock(block: ScBlockExpr): Unit = block.exprs.foreach(processExpression)
}
