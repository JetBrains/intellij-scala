package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, FinishElementInstruction, PopInstruction, PushValueInstruction}
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

class ScalaDfaControlFlowBuilder(private val body: ScExpression, private val factory: DfaValueFactory) {

  private val flow = new ControlFlow(factory, body)

  def buildFlow(): Option[ControlFlow] = {
    processExpression(body)
    popReturnValue()
    flow.finish()
    Some(flow)
  }

  private def popReturnValue(): Unit = flow.addInstruction(new PopInstruction)

  // TODO refactor into a different design later, once some basic version works
  private def processExpression(expression: ScExpression): Unit = {
    expression match {
      case block: ScBlockExpr => processBlock(block)
      case literal: ScLiteral => processLiteral(literal)
      case _ => println(s"Unsupported expression: $expression")
    }

    flow.finishElement(expression)
  }

  private def processBlock(block: ScBlockExpr): Unit = {
    // TODO can it be done in a prettier way?
    val expressions = block.exprs
    if (expressions.isEmpty) {
      pushUnknownValue()
    } else {
      expressions.foreach { expression =>
        processExpression(expression)
        if (expression != expressions.last) {
          flow.addInstruction(new PopInstruction)
        }
      }

      flow.addInstruction(new FinishElementInstruction(block))
    }
  }

  private def processLiteral(literal: ScLiteral): Unit = {
    flow.addInstruction(new PushValueInstruction(ScalaDfaTypeUtils.literalToDfType(literal), ScalaExpressionAnchor(literal)))
  }

  private def pushUnknownValue(): Unit = flow.addInstruction(new PushValueInstruction(DfType.TOP))
}
