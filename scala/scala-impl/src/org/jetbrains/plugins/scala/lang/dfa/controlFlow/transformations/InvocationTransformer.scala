package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, NotInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.{DfIntegralType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.RelationType
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir.ScalaInvocationInstruction
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.CollectionAccessAssertions.addCollectionAccessAssertions
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.SyntheticOperators._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{extractExpressionFromArgument, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

class InvocationTransformer(val wrappedInvocation: ScExpression)
  extends ExpressionTransformer(wrappedInvocation) {

  override def toString: String = s"InvocationTransformer: $wrappedInvocation"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    val invocationsInfo = wrappedInvocation match {
      case methodCall: ScMethodCall => InvocationInfo.fromMethodCall(methodCall)
      case methodInvocation: MethodInvocation => List(InvocationInfo.fromMethodInvocation(methodInvocation))
      case referenceExpression: ScReferenceExpression => List(InvocationInfo.fromReferenceExpression(referenceExpression))
      case constructorInvocation: ScNewTemplateDefinition => List(InvocationInfo.fromConstructorInvocation(constructorInvocation))
      case _ => throw TransformationFailedException(wrappedInvocation, "Expression of this type cannot be transformed as an invocation.")
    }

    if (!tryTransformIntoSpecialRepresentation(invocationsInfo, builder)) {
      invocationsInfo.tail.foreach(invocation => {
        transformMethodInvocation(invocation, builder)
        builder.popReturnValue()
      })

      transformMethodInvocation(invocationsInfo.head, builder)
    }
  }

  private def addAdditionalAssertions(invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Unit = {
    invocationInfo.thisArgument
      .flatMap(extractExpressionFromArgument)
      .foreach(thisExpression => addNotNullAssertion(thisExpression, wrappedInvocation, builder))

    addCollectionAccessAssertions(wrappedInvocation, invocationInfo, builder)
  }

  private def transformMethodInvocation(invocationInfo: InvocationInfo, builder: ScalaDfaControlFlowBuilder): Unit = {
    addAdditionalAssertions(invocationInfo, builder)

    val args = invocationInfo.argListsInEvaluationOrder.flatten
    args.foreach(_.content.transform(builder))

    val transfer = builder.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    builder.pushInstruction(new ScalaInvocationInstruction(invocationInfo,
      ScalaStatementAnchor(wrappedInvocation), transfer))
  }

  private def tryTransformIntoSpecialRepresentation(invocationsInfo: Seq[InvocationInfo], builder: ScalaDfaControlFlowBuilder): Boolean = {
    if (invocationsInfo.size > 1) return false
    val invocationInfo = invocationsInfo.head

    invocationInfo.invokedElement match {
      case Some(InvokedElement(psiElement)) => psiElement match {
        case function: ScSyntheticFunction => tryTransformSyntheticFunctionSpecially(function, invocationInfo, builder)
        case _ => false
      }
      case _ => false
    }
  }

  private def tryTransformSyntheticFunctionSpecially(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                     builder: ScalaDfaControlFlowBuilder): Boolean = {
    if (verifyArgumentsForBinaryOperator(invocationInfo.argListsInEvaluationOrder)) {
      if (tryTransformBinaryNumericOperator(function, invocationInfo, builder)) true
      else if (tryTransformBinaryRelationalOperator(function, invocationInfo, builder)) true
      else if (tryTransformBinaryLogicalOperator(function, invocationInfo, builder)) true
      else false
    } else if (verifyArgumentsForUnaryOperator(invocationInfo.argListsInEvaluationOrder)) {
      if (tryTransformUnaryNumericOperator(function, invocationInfo, builder)) true
      else if (tryTransformUnaryLogicalOperator(function, invocationInfo, builder)) true
      else false
    } else false
  }

  // TODO extract special support for operators
  private def matchesSignature(function: ScSyntheticFunction, functionName: String, returnedClass: String): Boolean = {
    val properReturnedClass = function.retType.extractClass match {
      case Some(returnClass) if returnClass.qualifiedName == returnedClass => true
      case _ => false
    }
    function.name == functionName && properReturnedClass
  }

  private def verifyArgumentsForUnaryOperator(arguments: List[List[Argument]]): Boolean = {
    arguments.size == 1 && arguments.head.size == 1
  }

  private def verifyArgumentsForBinaryOperator(arguments: List[List[Argument]]): Boolean = {
    arguments.size == 1 && arguments.head.size == 2
  }

  private def argumentsForBinarySyntheticOperator(invocationInfo: InvocationInfo): (Argument, Argument) = {
    val args = invocationInfo.argListsInEvaluationOrder
    assert(verifyArgumentsForBinaryOperator(args))
    val List(leftArg, rightArg) = args.head
    (leftArg, rightArg)
  }

  private def tryTransformBinaryNumericOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (typeClass <- List(ScalaInt, ScalaLong); operationName <- NumericBinary.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
        leftArg.content.transform(builder)
        // TODO check implicit conversions etc.
        // TODO check division by zero
        rightArg.content.transform(builder)
        builder.pushInstruction(new NumericBinaryInstruction(NumericBinary(operationName), ScalaStatementAnchor(wrappedInvocation)))
        return true
      }
    }

    false
  }

  private def tryTransformBinaryRelationalOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                   builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (operationName <- RelationalBinary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val operation = RelationalBinary(operationName)
        val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
        leftArg.content.transform(builder)
        // TODO check implicit conversions etc.
        // TODO check division by zero
        rightArg.content.transform(builder)
        builder.pushInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent,
          ScalaStatementAnchor(wrappedInvocation)))
        return true
      }
    }

    false
  }

  private def tryTransformBinaryLogicalOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (operationName <- LogicalBinary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)

        val anchor = ScalaStatementAnchor(wrappedInvocation)
        val endOffset = new DeferredOffset
        val nextConditionOffset = new DeferredOffset

        leftArg.content.transform(builder)

        val valueNeededToContinue = LogicalBinary(operationName) == LogicalOperation.And
        builder.pushInstruction(new ConditionalGotoInstruction(nextConditionOffset,
          DfTypes.booleanValue(valueNeededToContinue)))
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

  private def tryTransformUnaryNumericOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                               builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (typeClass <- List(ScalaInt, ScalaLong); operationName <- NumericUnary.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head
        val returnDfType = scTypeToDfType(function.retType).asInstanceOf[DfIntegralType]

        builder.pushInstruction(new PushValueInstruction(returnDfType.meetRange(LongRangeSet.point(0L))))
        singleThisArg.content.transform(builder)

        builder.pushInstruction(new NumericBinaryInstruction(NumericUnary(operationName), ScalaStatementAnchor(wrappedInvocation)))
        return true
      }
    }

    false
  }

  private def tryTransformUnaryLogicalOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                               builder: ScalaDfaControlFlowBuilder): Boolean = {
    for (operationName <- LogicalUnary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head
        singleThisArg.content.transform(builder)

        LogicalUnary(operationName) match {
          case LogicalOperation.Not => builder.pushInstruction(new NotInstruction(ScalaStatementAnchor(wrappedInvocation)))
            return true
          case _ =>
        }
      }
    }

    false
  }
}
