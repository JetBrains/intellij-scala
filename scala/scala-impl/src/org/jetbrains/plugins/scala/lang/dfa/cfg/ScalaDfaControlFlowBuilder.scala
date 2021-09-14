package org.jetbrains.plugins.scala.lang.dfa.cfg

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, JvmPushInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.{DfaValueFactory, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.{InfixOperators, literalToDfType}
import org.jetbrains.plugins.scala.lang.dfa.{LogicalOperation, ScalaStatementAnchor, ScalaUnreportedElementAnchor, cfg}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

class ScalaDfaControlFlowBuilder(private val body: ScBlockStatement, private val factory: DfaValueFactory) {

  private val flow = new ControlFlow(factory, body)
  private val trapTracker = new TrapTracker(factory, body)

  def buildFlow(): Option[ControlFlow] = {
    processElement(body)
    pushInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), null))
    popReturnValue()

    flow.finish()
    Some(flow)
  }

  // TODO extract those reusable util methods, separate from actual expressions logic
  private def pushInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  private def pushUnknownValue(): Unit = pushInstruction(new PushValueInstruction(DfType.TOP))

  private def setOffset(offset: DeferredOffset): Unit = offset.setOffset(flow.getInstructionCount)

  // TODO rewrite later into a proper instruction
  private def pushUnknownCall(statement: ScBlockStatement, argCount: Int): Unit = {
    popArguments(argCount)

    val resultType = DfType.TOP // TODO collect more precise information on type
    pushInstruction(new PushValueInstruction(resultType, ScalaStatementAnchor(statement)))
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
  private def processElement(element: ScalaPsiElement): Unit = {
    element match {
      case block: ScBlockExpr => processBlock(block)
      case literal: ScLiteral => processLiteral(literal)
      case infixExpression: ScInfixExpr => processInfixExpression(infixExpression)
      case ifExpression: ScIf => processIfExpression(ifExpression)
      case patternDefinition: ScPatternDefinition => processPatternDefinition(patternDefinition)
      case referenceExpression: ScReferenceExpression => processReferenceExpression(referenceExpression)
      case _ => println(s"Unsupported PSI element: $element")
    }

    flow.finishElement(element)
  }

  private def processExpressionIfPresent(container: Option[ScExpression]): Unit = container match {
    case Some(expression) => processElement(expression)
    case None => pushUnknownValue()
  }

  private def processBlock(block: ScBlockExpr): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        processElement(statement)
        popReturnValue()
      }

      processElement(statements.last)
      pushInstruction(new FinishElementInstruction(block))
    }
  }

  private def processLiteral(literal: ScLiteral): Unit = {
    pushInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  // TODO more comprehensive handling later
  private def processInfixExpression(infixExpression: ScInfixExpr): Unit = {
    val operationToken = infixExpression.operation.bind().get.name

    if (InfixOperators.Arithmetic.contains(operationToken)) {
      processArithmeticExpression(infixExpression, InfixOperators.Arithmetic(operationToken))
    } else if (InfixOperators.Relational.contains(operationToken)) {
      processRelationalExpression(infixExpression, InfixOperators.Relational(operationToken))
    } else if (InfixOperators.Logical.contains(operationToken)) {
      processLogicalExpression(infixExpression, InfixOperators.Logical(operationToken))
    } else {
      processElement(infixExpression.left)
      processElement(infixExpression.right)
      pushUnknownCall(infixExpression, 2)
    }
  }

  private def processArithmeticExpression(expression: ScInfixExpr, operation: LongRangeBinOp): Unit = {
    processElement(expression.left)
    // TODO check implicit conversions etc.
    // TODO check division by zero
    processElement(expression.right)
    pushInstruction(new NumericBinaryInstruction(operation, ScalaStatementAnchor(expression)))
  }

  private def processRelationalExpression(expression: ScInfixExpr, operation: RelationType): Unit = {
    val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
    processElement(expression.left)
    // TODO check types, for now we only want this (except for equality) to work on JVM primitive types, otherwise pushUnknownCall
    // TODO add implicit conversions etc.
    processElement(expression.right)
    pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaStatementAnchor(expression)))
  }

  private def processLogicalExpression(expression: ScInfixExpr, operation: LogicalOperation): Unit = {
    val anchor = ScalaStatementAnchor(expression)
    val endOffset = new DeferredOffset
    val nextConditionOffset = new DeferredOffset

    processElement(expression.left)

    val valueNeededToContinue = operation == LogicalOperation.And
    pushInstruction(new ConditionalGotoInstruction(nextConditionOffset,
      DfTypes.booleanValue(valueNeededToContinue), expression.left))
    pushInstruction(new PushValueInstruction(DfTypes.booleanValue(!valueNeededToContinue), anchor))
    pushInstruction(new GotoInstruction(endOffset))

    setOffset(nextConditionOffset)
    pushInstruction(new FinishElementInstruction(null))
    processElement(expression.right)
    setOffset(endOffset)
    pushInstruction(new ResultOfInstruction(anchor))
  }

  private def processIfExpression(expression: ScIf): Unit = {
    for (condition <- expression.condition) {
      val skipThenOffset = new DeferredOffset
      val skipElseOffset = new DeferredOffset

      processElement(condition)
      pushInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))

      pushInstruction(new FinishElementInstruction(null))
      processExpressionIfPresent(expression.thenExpression)
      pushInstruction(new GotoInstruction(skipElseOffset))
      setOffset(skipThenOffset)

      pushInstruction(new FinishElementInstruction(null))
      processExpressionIfPresent(expression.elseExpression)
      setOffset(skipElseOffset)

      pushInstruction(new FinishElementInstruction(expression))
    }
  }

  private def processPatternDefinition(definition: ScPatternDefinition): Unit = {
    if (!definition.isSimple) {
      pushUnknownValue()
      return
    }

    val binding = definition.bindings.head
    val dfaVariable = factory.getVarFactory.createVariableValue(cfg.ScalaVariableDescriptor(binding, binding.isStable))

    processExpressionIfPresent(definition.expr)
    pushInstruction(new SimpleAssignmentInstruction(ScalaStatementAnchor(definition), dfaVariable))
  }

  private def processReferenceExpression(expression: ScReferenceExpression): Unit = {
    // TODO add qualified expressions, currently only simple ones
    expression.getReference.bind().map(_.element) match {
      // TODO check isStable, what exactly does it mean in those places?
      case Some(element) => // TODO extract later + try to fix types/anchor, if possible
        val dfaVariable = factory.getVarFactory.createVariableValue(cfg.ScalaVariableDescriptor(element, isStable = true))
        pushInstruction(new JvmPushInstruction(dfaVariable, ScalaUnreportedElementAnchor(element)))
      case _ => pushUnknownValue()
    }
  }
}
