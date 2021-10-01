package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.LogicalOperation
import org.jetbrains.plugins.scala.lang.dfa.utils.SpecialSupportUtils._
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction


class InvocationTransformer(val invocation: MethodInvocation) extends ExpressionTransformer(invocation) {

  override def toString: String = s"InvocationTransformer: $invocation"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    if (!tryTransformWithSpecialSupport(builder)) {
      //      builder.pushUnknownCall(invocation, invocationInfo.argsInEvaluationOrder.size)
      builder.pushUnknownValue()
    }
  }

  // TODO don't rely on the order of args in arInEvaluationOrder but on the mapping from params to the index in this list
  private def tryTransformWithSpecialSupport(builder: ScalaDfaControlFlowBuilder): Boolean = {
    val invocationInfo = InvocationInfo.fromMethodInvocation(invocation)
    invocationInfo.invokedElement match {
      case Some(InvokedElement(psiElement)) => psiElement match {
        case function: ScSyntheticFunction => tryTransformSyntheticFunctionSpecially(function, invocationInfo, builder)
        case function: ScFunction => tryTransformNormalFunctionSpecially(function, invocationInfo, builder)
        case _ => false
      }
      case _ => false
    }
  }

  private def tryTransformSyntheticFunctionSpecially(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                     builder: ScalaDfaControlFlowBuilder): Boolean = {
    if (tryTransformNumericOperations(function, invocationInfo, builder)) true
    else if (tryTransformRelationalExpressions(function, invocationInfo, builder)) true
    else if (tryTransformLogicalOperations(function, invocationInfo, builder)) true
    else false
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

  private def tryTransformNumericOperations(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                            builder: ScalaDfaControlFlowBuilder): Boolean = {
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

  private def tryTransformRelationalExpressions(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (typeClass <- NumericTypeClasses; operationName <- RelationalOperations.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val operation = RelationalOperations(operationName)
        val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
        val List(leftArg, rightArg, _*) = invocationInfo.argsInEvaluationOrder
        leftArg.content.transform(builder)
        // TODO check implicit conversions etc.
        // TODO check division by zero
        rightArg.content.transform(builder)
        builder.pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaStatementAnchor(invocation)))
        return true
      }
    }

    false
  }

  private def tryTransformLogicalOperations(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                            builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (operationName <- LogicalOperations.keys) {
      if (matchesSignature(function, operationName, BooleanTypeClass)) {
        val operation = LogicalOperations(operationName)
        val List(leftArg, rightArg, _*) = invocationInfo.argsInEvaluationOrder

        val anchor = ScalaStatementAnchor(invocation)
        val endOffset = new DeferredOffset
        val nextConditionOffset = new DeferredOffset

        leftArg.content.transform(builder)

        val valueNeededToContinue = operation == LogicalOperation.And
        builder.pushInstruction(new ConditionalGotoInstruction(nextConditionOffset,
          DfTypes.booleanValue(valueNeededToContinue), invocation.argumentExpressions.head))
        builder.pushInstruction(new PushValueInstruction(DfTypes.booleanValue(!valueNeededToContinue), anchor))
        builder.pushInstruction(new GotoInstruction(endOffset))

        builder.setOffset(nextConditionOffset)
        builder.pushInstruction(new FinishElementInstruction(null))
        rightArg.content.transform(builder)
        builder.setOffset(endOffset)
        builder.pushInstruction(new ResultOfInstruction(anchor))
        return true
      }
    }

    false
  }
}
