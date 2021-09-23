package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.java.inst.NumericBinaryInstruction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.SpecialSupport.{NumericOperations, NumericTypeClasses}
import org.jetbrains.plugins.scala.lang.dfa.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction


class InvocationTransformer(invocation: MethodInvocation) extends ScalaPsiElementTransformer(invocation) {

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    if (!tryTransformWithSpecialSupport(builder)) {
      //      builder.pushUnknownCall(invocation, invocationInfo.argsInEvaluationOrder.size)
      builder.pushUnknownValue()
    }
  }

  private def tryTransformWithSpecialSupport(builder: ScalaDfaControlFlowBuilder): Boolean = {
    val invocationInfo = InvocationInfo.fromMethodInvocation(invocation)
    invocationInfo.invokedElement match {
      case Some(function: ScSyntheticFunction) => tryTransformSyntheticFunctionSpecially(function, invocationInfo, builder)
      case Some(function: ScFunction) => tryTransformNormalFunctionSpecially(function, invocationInfo, builder)
      case _ => false
    }
  }

  private def tryTransformSyntheticFunctionSpecially(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                     builder: ScalaDfaControlFlowBuilder): Boolean = {
    if (tryTransformNumericOperations(function, invocationInfo, builder)) true
    else false // TODO instead of "false" other cases
  }

  private def tryTransformNormalFunctionSpecially(function: ScFunction, invocationInfo: InvocationInfo,
                                                  builder: ScalaDfaControlFlowBuilder): Boolean = {
    false
  }

  private def matchesSignature(function: ScSyntheticFunction, functionName: String, returnedClass: String): Boolean = {
    val properReturnedClass = function.retType.extractClass match {
      case Some(returnClass) if returnClass.qualifiedName == returnedClass => true
      case _ => false
    }
    function.name == functionName && properReturnedClass
  }

  private def tryTransformNumericOperations(function: ScSyntheticFunction, invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Boolean = {
    tryTransformNumericOperations(function, invocationInfo, builder)
    for (typeClass <- NumericTypeClasses; operationName <- NumericOperations.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val operation = NumericOperations(operationName)
        val List(leftArg, rightArg, _*) = invocationInfo.argsInEvaluationOrder
        leftArg.content.transform(builder)
        // TODO check implicit conversions etc.
        // TODO check division by zero
        rightArg.content.transform(builder)
        builder.pushInstruction(new NumericBinaryInstruction(operation, ScalaStatementAnchor(invocation)))
        return true
      }
    }

    false
  }

  /*
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
   */
}
