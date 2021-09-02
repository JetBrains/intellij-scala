package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValueFactory, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.{InfixOperators, literalToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScInfixExpr}

class ScalaDfaControlFlowBuilder(private val body: ScExpression, private val factory: DfaValueFactory) {

  private val flow = new ControlFlow(factory, body)
  private val trapTracker = new TrapTracker(factory, body)

  def buildFlow(): Option[ControlFlow] = {
    processExpression(body)
    popReturnValue()
    flow.finish()
    Some(flow)
  }

  private def pushInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  private def pushUnknownValue(): Unit = pushInstruction(new PushValueInstruction(DfType.TOP))

  // TODO rewrite later into a proper instruction
  private def pushUnknownCall(expression: ScExpression, argCount: Int): Unit = {
    popArguments(argCount)

    val resultType = DfType.TOP // TODO collect more precise information on type
    pushInstruction(new PushValueInstruction(resultType, ScalaExpressionAnchor(expression)))
    pushInstruction(new FlushFieldsInstruction)

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach(transfer => pushInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer)))
  }

  private def popReturnValue(): Unit = pushInstruction(new PopInstruction)

  private def popArguments(argCount: Int): Unit = {
    if (argCount > 1) {
      pushInstruction(new SpliceInstruction(argCount))
    } else if (argCount == 1) {
      pushInstruction(new PopInstruction)
    }
  }

  // TODO refactor into a different design later, once some basic version works
  private def processExpression(expression: ScExpression): Unit = {
    expression match {
      case block: ScBlockExpr => processBlock(block)
      case literal: ScLiteral => processLiteral(literal)
      case infixExpression: ScInfixExpr => processInfixExpression(infixExpression)
      case _ => println(s"Unsupported expression: $expression")
    }

    flow.finishElement(expression)
  }

  private def processBlock(block: ScBlockExpr): Unit = {
    val expressions = block.exprs
    if (expressions.isEmpty) {
      pushUnknownValue()
    } else {
      expressions.init.foreach { expression =>
        processExpression(expression)
        popReturnValue()
      }

      processExpression(expressions.last)
      pushInstruction(new FinishElementInstruction(block))
    }
  }

  private def processLiteral(literal: ScLiteral): Unit = {
    pushInstruction(new PushValueInstruction(literalToDfType(literal), ScalaExpressionAnchor(literal)))
  }

  // TODO more comprehensive handling later
  private def processInfixExpression(infixExpression: ScInfixExpr): Unit = {
    val operationToken = infixExpression.operation.refName

    if (InfixOperators.Arithmetic.contains(operationToken)) {
      processArithmeticExpression(infixExpression, InfixOperators.Arithmetic(operationToken))
    } else if (InfixOperators.Relational.contains(operationToken)) {
      processRelationalExpression(infixExpression, InfixOperators.Relational(operationToken))
    } else if (InfixOperators.Logical.contains(operationToken)) {
      // TODO handle
    } else {
      processExpression(infixExpression.left)
      processExpression(infixExpression.right)
      pushUnknownCall(infixExpression, 2)
    }
  }

  private def processArithmeticExpression(expression: ScInfixExpr, operation: LongRangeBinOp): Unit = {
    processExpression(expression.left)
    // TODO check implicit conversions etc.
    // TODO check division by zero
    processExpression(expression.right)
    pushInstruction(new NumericBinaryInstruction(operation, ScalaExpressionAnchor(expression)))
  }

  private def processRelationalExpression(expression: ScInfixExpr, operation: RelationType): Unit = {
    val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
    processExpression(expression.left)
    // TODO check types, for now we only want this (except for equality) to work on JVM primitive types, otherwise pushUnknownCall
    // TODO add implicit conversions etc.
    processExpression(expression.right)
    pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaExpressionAnchor(expression)))
  }
}
