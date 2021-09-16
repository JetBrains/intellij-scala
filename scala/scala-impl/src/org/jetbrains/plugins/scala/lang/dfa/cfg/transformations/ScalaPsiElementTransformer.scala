package org.jetbrains.plugins.scala.lang.dfa.cfg.transformations

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, JvmPushInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.{InfixOperators, literalToDfType}
import org.jetbrains.plugins.scala.lang.dfa.cfg.{ScalaDfaControlFlowBuilder, ScalaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.{LogicalOperation, ScalaStatementAnchor, ScalaUnreportedElementAnchor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

class ScalaPsiElementTransformer(element: ScalaPsiElement) extends Transformable {

  // TODO extract this further to other transformers
  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    element match {
      case block: ScBlockExpr => processBlock(builder, block)
      case parenthesisedExpression: ScParenthesisedExpr => processParenthesisedExpression(builder, parenthesisedExpression)
      case literal: ScLiteral => processLiteral(builder, literal)
      case _: ScUnitExpr => processUnitExpression(builder)
      case infixExpression: ScInfixExpr => processInfixExpression(builder, infixExpression)
      case ifExpression: ScIf => processIfExpression(builder, ifExpression)
      case patternDefinition: ScPatternDefinition => processPatternDefinition(builder, patternDefinition)
      case referenceExpression: ScReferenceExpression => processReferenceExpression(builder, referenceExpression)
      case _ => throw TransformationFailedException(element, "Unsupported PSI element.")
    }

    builder.finishElement(element)
  }

  private def wrapAndTransform(builder: ScalaDfaControlFlowBuilder, element: ScalaPsiElement): Unit = {
    new ScalaPsiElementTransformer(element).transform(builder)
  }

  private def processExpressionIfPresent(builder: ScalaDfaControlFlowBuilder, container: Option[ScExpression]): Unit = container match {
    case Some(expression) => wrapAndTransform(builder, expression)
    case None => builder.pushUnknownValue()
  }

  private def processBlock(builder: ScalaDfaControlFlowBuilder, block: ScBlockExpr): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        wrapAndTransform(builder, statement)
        builder.popReturnValue()
      }

      wrapAndTransform(builder, statements.last)
      builder.pushInstruction(new FinishElementInstruction(block))
    }
  }

  private def processParenthesisedExpression(builder: ScalaDfaControlFlowBuilder, expression: ScParenthesisedExpr): Unit = {
    expression.innerElement.foreach(wrapAndTransform(builder, _))
  }

  private def processLiteral(builder: ScalaDfaControlFlowBuilder, literal: ScLiteral): Unit = {
    builder.pushInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def processUnitExpression(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownValue()

  // TODO more comprehensive handling later
  private def processInfixExpression(builder: ScalaDfaControlFlowBuilder, infixExpression: ScInfixExpr): Unit = {
    val operationToken = infixExpression.operation.bind().getOrElse {
      builder.pushUnknownCall(infixExpression, 0)
      return
    }.name

    if (InfixOperators.Arithmetic.contains(operationToken)) {
      processArithmeticExpression(builder, infixExpression, InfixOperators.Arithmetic(operationToken))
    } else if (InfixOperators.Relational.contains(operationToken)) {
      processRelationalExpression(builder, infixExpression, InfixOperators.Relational(operationToken))
    } else if (InfixOperators.Logical.contains(operationToken)) {
      processLogicalExpression(builder, infixExpression, InfixOperators.Logical(operationToken))
    } else {
      wrapAndTransform(builder, infixExpression.left)
      wrapAndTransform(builder, infixExpression.right)
      builder.pushUnknownCall(infixExpression, 2)
    }
  }

  private def processArithmeticExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: LongRangeBinOp): Unit = {
    wrapAndTransform(builder, expression.left)
    // TODO check implicit conversions etc.
    // TODO check division by zero
    wrapAndTransform(builder, expression.right)
    builder.pushInstruction(new NumericBinaryInstruction(operation, ScalaStatementAnchor(expression)))
  }

  private def processRelationalExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: RelationType): Unit = {
    val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
    wrapAndTransform(builder, expression.left)
    // TODO check types, for now we only want this (except for equality) to work on JVM primitive types, otherwise pushUnknownCall
    // TODO add implicit conversions etc.
    wrapAndTransform(builder, expression.right)
    builder.pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaStatementAnchor(expression)))
  }

  private def processLogicalExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: LogicalOperation): Unit = {
    val anchor = ScalaStatementAnchor(expression)
    val endOffset = new DeferredOffset
    val nextConditionOffset = new DeferredOffset

    wrapAndTransform(builder, expression.left)

    val valueNeededToContinue = operation == LogicalOperation.And
    builder.pushInstruction(new ConditionalGotoInstruction(nextConditionOffset,
      DfTypes.booleanValue(valueNeededToContinue), expression.left))
    builder.pushInstruction(new PushValueInstruction(DfTypes.booleanValue(!valueNeededToContinue), anchor))
    builder.pushInstruction(new GotoInstruction(endOffset))

    builder.setOffset(nextConditionOffset)
    builder.pushInstruction(new FinishElementInstruction(null))
    wrapAndTransform(builder, expression.right)
    builder.setOffset(endOffset)
    builder.pushInstruction(new ResultOfInstruction(anchor))
  }

  private def processIfExpression(builder: ScalaDfaControlFlowBuilder, expression: ScIf): Unit = {
    for (condition <- expression.condition) {
      val skipThenOffset = new DeferredOffset
      val skipElseOffset = new DeferredOffset

      wrapAndTransform(builder, condition)
      builder.pushInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))

      builder.pushInstruction(new FinishElementInstruction(null))
      processExpressionIfPresent(builder, expression.thenExpression)
      builder.pushInstruction(new GotoInstruction(skipElseOffset))
      builder.setOffset(skipThenOffset)

      builder.pushInstruction(new FinishElementInstruction(null))
      processExpressionIfPresent(builder, expression.elseExpression)
      builder.setOffset(skipElseOffset)

      builder.pushInstruction(new FinishElementInstruction(expression))
    }
  }

  private def processPatternDefinition(builder: ScalaDfaControlFlowBuilder, definition: ScPatternDefinition): Unit = {
    if (!definition.isSimple) {
      builder.pushUnknownValue()
      return
    }

    val binding = definition.bindings.head
    val dfaVariable = builder.createVariable(ScalaVariableDescriptor(binding, binding.isStable))

    processExpressionIfPresent(builder, definition.expr)
    builder.pushInstruction(new SimpleAssignmentInstruction(ScalaStatementAnchor(definition), dfaVariable))
  }

  private def processReferenceExpression(builder: ScalaDfaControlFlowBuilder, expression: ScReferenceExpression): Unit = {
    // TODO add qualified expressions, currently only simple ones
    expression.getReference.bind().map(_.element) match {
      // TODO check isStable, what exactly does it mean in those places?
      case Some(element) => // TODO extract later + try to fix types/anchor, if possible
        val dfaVariable = builder.createVariable(ScalaVariableDescriptor(element, isStable = true))
        builder.pushInstruction(new JvmPushInstruction(dfaVariable, ScalaUnreportedElementAnchor(element)))
      case _ => builder.pushUnknownCall(expression, 0)
    }
  }
}
