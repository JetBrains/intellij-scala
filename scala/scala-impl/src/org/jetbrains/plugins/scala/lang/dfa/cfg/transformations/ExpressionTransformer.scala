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
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class ExpressionTransformer(expression: ScExpression) extends ScalaPsiElementTransformer(expression) {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = expression match {
    case block: ScBlockExpr => transformBlock(block, builder)
    case parenthesised: ScParenthesisedExpr => transformParenthesisedExpression(parenthesised, builder)
    case literal: ScLiteral => transformLiteral(literal, builder)
    case _: ScUnitExpr => transformUnitExpression(builder)
    case ifExpression: ScIf => transformIfExpression(ifExpression, builder)
    case reference: ScReferenceExpression => transformReferenceExpression(reference, builder)
    case invocation: MethodInvocation => transformInvocation(invocation, builder)
    case templateDefinition: ScTemplateDefinition => transformTemplateDefinition(templateDefinition, builder)
    //    case infixExpression: ScInfixExpr => transformInfixExpression(infixExpression, builder)
    case _ => throw TransformationFailedException(expression, "Unsupported expression.")
  }

  private def transformBlock(block: ScBlockExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    val statements = block.statements
    if (statements.isEmpty) {
      builder.pushUnknownValue()
    } else {
      statements.init.foreach { statement =>
        new ScalaPsiElementTransformer(statement).transform(builder)
        builder.popReturnValue()
      }

      transformPsiElement(statements.last, builder)
      builder.pushInstruction(new FinishElementInstruction(block))
    }
  }

  private def transformParenthesisedExpression(parenthesised: ScParenthesisedExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
    parenthesised.innerElement.foreach(transformPsiElement(_, builder))
  }

  private def transformLiteral(literal: ScLiteral, builder: ScalaDfaControlFlowBuilder): Unit = {
    builder.pushInstruction(new PushValueInstruction(literalToDfType(literal), ScalaStatementAnchor(literal)))
  }

  private def transformUnitExpression(builder: ScalaDfaControlFlowBuilder): Unit = builder.pushUnknownValue()

  private def transformIfExpression(ifExpression: ScIf, builder: ScalaDfaControlFlowBuilder): Unit = {
    for (condition <- ifExpression.condition) {
      val skipThenOffset = new DeferredOffset
      val skipElseOffset = new DeferredOffset

      transformPsiElement(condition, builder)
      builder.pushInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))

      builder.pushInstruction(new FinishElementInstruction(null))
      transformIfPresent(ifExpression.thenExpression, builder)
      builder.pushInstruction(new GotoInstruction(skipElseOffset))
      builder.setOffset(skipThenOffset)

      builder.pushInstruction(new FinishElementInstruction(null))
      transformIfPresent(ifExpression.elseExpression, builder)
      builder.setOffset(skipElseOffset)

      builder.pushInstruction(new FinishElementInstruction(ifExpression))
    }
  }

  private def transformReferenceExpression(expression: ScReferenceExpression, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO add qualified expressions, currently only simple ones
    expression.getReference.bind().map(_.element) match {
      case Some(element) => // TODO extract later + try to fix types/anchor, if possible
        val dfaVariable = builder.createVariable(ScalaVariableDescriptor(element, isStable = true))
        builder.pushInstruction(new JvmPushInstruction(dfaVariable, ScalaUnreportedElementAnchor(element)))
      case _ => builder.pushUnknownCall(expression, 0)
    }
  }

  def transformInvocation(invocation: MethodInvocation, builder: ScalaDfaControlFlowBuilder): Unit = {
    new InvocationTransformer(invocation).transform(builder)
  }

  def transformTemplateDefinition(templateDefinition: ScTemplateDefinition, builder: ScalaDfaControlFlowBuilder): Unit = {
    // TODO implement
    builder.pushUnknownValue()
  }


  // TODO move all the methods below elsewhere once InvocationInfo works properly
  // convert this to invocation info then
  def transformInfixExpression(infixExpression: ScInfixExpr, builder: ScalaDfaControlFlowBuilder): Unit = {
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
      transformPsiElement(infixExpression.left, builder)
      transformPsiElement(infixExpression.right, builder)
      builder.pushUnknownCall(infixExpression, 2)
    }
  }

  private def processArithmeticExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: LongRangeBinOp): Unit = {
    transformPsiElement(expression.left, builder)
    // TODO check implicit conversions etc.
    // TODO check division by zero
    transformPsiElement(expression.right, builder)
    builder.pushInstruction(new NumericBinaryInstruction(operation, ScalaStatementAnchor(expression)))
  }

  private def processRelationalExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: RelationType): Unit = {
    val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
    transformPsiElement(expression.left, builder)
    // TODO check types, for now we only want this (except for equality) to work on JVM primitive types, otherwise pushUnknownCall
    // TODO add implicit conversions etc.
    transformPsiElement(expression.right, builder)
    builder.pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaStatementAnchor(expression)))
  }

  private def processLogicalExpression(builder: ScalaDfaControlFlowBuilder, expression: ScInfixExpr, operation: LogicalOperation): Unit = {
    val anchor = ScalaStatementAnchor(expression)
    val endOffset = new DeferredOffset
    val nextConditionOffset = new DeferredOffset

    transformPsiElement(expression.left, builder)

    val valueNeededToContinue = operation == LogicalOperation.And
    builder.pushInstruction(new ConditionalGotoInstruction(nextConditionOffset,
      DfTypes.booleanValue(valueNeededToContinue), expression.left))
    builder.pushInstruction(new PushValueInstruction(DfTypes.booleanValue(!valueNeededToContinue), anchor))
    builder.pushInstruction(new GotoInstruction(endOffset))

    builder.setOffset(nextConditionOffset)
    builder.pushInstruction(new FinishElementInstruction(null))
    transformPsiElement(expression.right, builder)
    builder.setOffset(endOffset)
    builder.pushInstruction(new ResultOfInstruction(anchor))
  }
}
