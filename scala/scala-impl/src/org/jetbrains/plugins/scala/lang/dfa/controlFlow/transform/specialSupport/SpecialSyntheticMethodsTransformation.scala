package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform
package specialSupport

import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, NotInstruction, NumericBinaryInstruction}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.{DfBooleanType, DfIntegralType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.LogicalOperation
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages.{ScalaBoolean, ScalaInt, ScalaLong}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

trait SpecialSyntheticMethodsTransformation { this: ScalaDfaControlFlowBuilder =>
  final def tryTransformSyntheticFunctionSpecially(function: ScSyntheticFunction,
                                                   invocationInfo: InvocationInfo,
                                                   invocation: ScExpression): Boolean = {
    if (verifyArgumentsForBinaryOperator(invocationInfo.argListsInEvaluationOrder)) {
      if (tryTransformBinaryNumericOperator(function, invocationInfo, invocation)) true
      else if (tryTransformBinaryRelationalOperator(function, invocationInfo, invocation)) true
      else if (tryTransformBinaryLogicalOperator(function, invocationInfo, invocation)) true
      else false
    } else if (verifyArgumentsForUnaryOperator(invocationInfo.argListsInEvaluationOrder)) {
      if (tryTransformUnaryNumericOperator(function, invocationInfo, invocation)) true
      else if (tryTransformUnaryLogicalOperator(function, invocationInfo, invocation)) true
      else false
    } else false
  }

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

  private def tryTransformBinaryOperands(leftArg: Argument, rightArg: Argument,
                                         forceEqualityByContent: Boolean): Boolean = {
    val leftExpression = leftArg.content
    val rightExpression = rightArg.content
    val balancedType = balanceType(
      leftExpression.map(resolveExpressionType),
      rightExpression.map(resolveExpressionType),
      forceEqualityByContent
    )

    transformExpression(leftArg.content, ResultReq.Required)
    buildImplicitConversion(leftExpression, balancedType)
    transformExpression(rightArg.content, ResultReq.Required)
    buildImplicitConversion(rightExpression, balancedType)

    val leftType = leftExpression.map(resolveExpressionType)
    val rightType = rightExpression.map(resolveExpressionType)

    (leftType, rightType) match {
      case (Some(left), Some(right)) => left == right || (isPrimitiveType(left) && isPrimitiveType(right))
      case _ => false
    }
  }

  private def tryTransformBinaryNumericOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                                invocation: ScExpression): Boolean = {
    for (typeClass <- List(ScalaInt, ScalaLong); operationName <- NumericBinary.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
        val successful = tryTransformBinaryOperands(leftArg, rightArg, forceEqualityByContent = false)

        if (successful)
          addInstruction(
            new NumericBinaryInstruction(
              NumericBinary(operationName),
              ScalaStatementAnchor(invocation)
            )
          )
        else buildUnknownCall(invocation, 2, ResultReq.Required)
        return true
      }
    }

    false
  }

  private def tryTransformBinaryRelationalOperator(function: ScSyntheticFunction,
                                                   invocationInfo: InvocationInfo,
                                                   invocation: ScExpression): Boolean = {
    for (operationName <- RelationalBinary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val operation = RelationalBinary(operationName)
        val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
        val successful = tryTransformBinaryOperands(leftArg, rightArg, forceEqualityByContent)

        if (successful) {
          addInstruction(new BooleanBinaryInstruction(operation, forceEqualityByContent, ScalaStatementAnchor(invocation)))
        } else {
          buildUnknownCall(invocation, 2, ResultReq.Required)
        }
        return true
      }
    }

    false
  }

  private def tryTransformBinaryLogicalOperator(function: ScSyntheticFunction,
                                                invocationInfo: InvocationInfo,
                                                invocation: ScExpression): Boolean = {
    for (operationName <- LogicalBinary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
        if (verifyBooleanArgumentType(leftArg.content) || verifyBooleanArgumentType(rightArg.content)) {
          val anchor = ScalaStatementAnchor(invocation)
          val nextConditionLabel = newLabel()
          val endLabel = newLabel()

          transformExpression(leftArg.content, ResultReq.Required)

          val valueNeededToContinue = LogicalBinary(operationName) == LogicalOperation.And
          addInstruction(new ConditionalGotoInstruction(nextConditionLabel, DfTypes.booleanValue(valueNeededToContinue)))
          push(DfTypes.booleanValue(!valueNeededToContinue), anchor)
          goto(endLabel)

          anchorLabel(nextConditionLabel)
          addInstruction(new FinishElementInstruction(null))
          transformExpression(rightArg.content, ResultReq.Required)
          anchorLabel(endLabel)
          addInstruction(new ResultOfInstruction(anchor))
          return true
        }
      }
    }

    false
  }

  private def tryTransformUnaryNumericOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                               invocation: ScExpression): Boolean = {
    for (typeClass <- List(ScalaInt, ScalaLong); operationName <- NumericUnary.keys) {
      if (matchesSignature(function, operationName, typeClass)) {
        val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head
        val returnDfType = scTypeToDfType(function.retType).asInstanceOf[DfIntegralType]

        addInstruction(new PushValueInstruction(returnDfType.meetRange(LongRangeSet.point(0L))))
        transformExpression(singleThisArg.content, ResultReq.Required)

        addInstruction(new NumericBinaryInstruction(NumericUnary(operationName), ScalaStatementAnchor(invocation)))
        return true
      }
    }

    false
  }

  private def tryTransformUnaryLogicalOperator(function: ScSyntheticFunction, invocationInfo: InvocationInfo,
                                               invocation: ScExpression): Boolean = {
    for (operationName <- LogicalUnary.keys) {
      if (matchesSignature(function, operationName, ScalaBoolean)) {
        val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head

        LogicalUnary(operationName) match {
          case LogicalOperation.Not if verifyBooleanArgumentType(singleThisArg.content) =>
            transformExpression(singleThisArg.content, ResultReq.Required)
            addInstruction(new NotInstruction(ScalaStatementAnchor(invocation)))
            return true
          case _ =>
        }
      }
    }

    false
  }

  private def verifyBooleanArgumentType(expression: Option[ScExpression]): Boolean = expression
    .map(resolveExpressionType)
    .map(scTypeToDfType)
    .exists(_.is[DfBooleanType])
}
