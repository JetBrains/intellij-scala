package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform
package specialSupport

import com.intellij.codeInspection.dataFlow.types.{DfBooleanType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.specialSupport.SpecialSyntheticMethodsTransformation.syntheticTransformations
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages.ScalaBoolean
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.{LogicalOperation, NumericPrimitives}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

trait SpecialSyntheticMethodsTransformation { this: ScalaDfaControlFlowBuilder =>
  final def tryTransformSyntheticFunctionSpecially(function: ScSyntheticFunction,
                                                   invocationInfo: InvocationInfo): Option[StackValue] = {
    val argCount = invocationInfo.argListsInEvaluationOrder match {
      case head :: Nil => head.size
      case _ => return None
    }

    val properReturnedClass = function.retType.extractClass match {
      case Some(returnClass) => returnClass.qualifiedName
      case _ => return None
    }

    syntheticTransformations
      .get((argCount, properReturnedClass, function.name))
      .map(f => f(this)(function, invocationInfo))
  }

  private def argumentsForBinarySyntheticOperator(invocationInfo: InvocationInfo): (Argument, Argument) = {
    val args = invocationInfo.argListsInEvaluationOrder
    val List(leftArg, rightArg) = args.head
    (leftArg, rightArg)
  }

  private def tryTransformBinaryOperands(leftArg: Argument,
                                         rightArg: Argument,
                                         forceEqualityByContent: Boolean): (StackValue, StackValue, Boolean) = {
    val leftExpression = leftArg.content
    val rightExpression = rightArg.content
    val leftType = leftExpression.map(resolveExpressionType)
    val rightType = rightExpression.map(resolveExpressionType)
    val balancedType = balanceType(
      leftType,
      rightType,
      forceEqualityByContent
    )

    val left = transformExpression(leftArg.content, ResultReq.Required)
    val leftConverted = convertPrimitiveIfNeeded(left, leftType, balancedType)
    val right = transformExpression(rightArg.content, ResultReq.Required)
    val rightConverted = convertPrimitiveIfNeeded(right, rightType, balancedType)

    val success = (leftType, rightType) match {
      case (Some(left), Some(right)) =>
        left == right || (isPrimitiveType(left) && isPrimitiveType(right))
      case _ =>
        false
    }
    (leftConverted, rightConverted, success)
  }


  private[SpecialSyntheticMethodsTransformation]
  def transformBinaryNumericOperator(function: ScSyntheticFunction,
                                     invocationInfo: InvocationInfo): StackValue = {
    val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
    val (left, right, successful) = tryTransformBinaryOperands(leftArg, rightArg, forceEqualityByContent = false)

    if (successful) {
      binaryNumOp(
        left,
        right,
        NumericBinary(function.name),
        invocationInfo.anchor
      )
    } else {
      buildUnknownCall(ResultReq.Required, Seq(left, right))
    }
  }

  private[SpecialSyntheticMethodsTransformation]
  def transformBinaryBooleanOperator(function: ScSyntheticFunction,
                                     invocationInfo: InvocationInfo): StackValue = {

    val operation = RelationalBinary(function.name)
    val forceEqualityByContent = operation == RelationType.EQ || operation == RelationType.NE
    val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
    val (left, right, successful) = tryTransformBinaryOperands(leftArg, rightArg, forceEqualityByContent)

    if (successful) {
      binaryBoolOp(left, right, operation, forceEqualityByContent, invocationInfo.anchor)
    } else {
      buildUnknownCall(ResultReq.Required, Seq(left, right))
    }
  }

  private[SpecialSyntheticMethodsTransformation]
  def transformBinaryLogicalOperator(function: ScSyntheticFunction,
                                     invocationInfo: InvocationInfo): StackValue = {
    val operation = LogicalBinary(function.name)
    val (leftArg, rightArg) = argumentsForBinarySyntheticOperator(invocationInfo)
    if (verifyBooleanArgumentType(leftArg.content) || verifyBooleanArgumentType(rightArg.content)) {
      val stack = stackSnapshot
      val anchor = invocationInfo.anchor
      val nextConditionLabel = newDeferredLabel()
      val endLabel = newDeferredLabel()

      val leftCond = transformExpression(leftArg.content, ResultReq.Required)

      val valueNeededToContinue = operation == LogicalOperation.And
      gotoIf(leftCond, DfTypes.booleanValue(valueNeededToContinue), nextConditionLabel)
      val resultLeft = push(DfTypes.booleanValue(!valueNeededToContinue), anchor)
      goto(endLabel)

      restore(stack)
      anchorLabel(nextConditionLabel)
      finish(null)
      val rightCond = transformExpression(rightArg.content, ResultReq.Required)

      val result = joinHere(resultLeft, rightCond)
      anchorLabel(endLabel)
      attachAnchor(result, anchor)
      result
    } else {
      val left = transformExpression(leftArg.content, ResultReq.Required)
      val right = transformExpression(rightArg.content, ResultReq.Required)
      buildUnknownCall(ResultReq.Required, Seq(left, right))
    }
  }

  private[SpecialSyntheticMethodsTransformation]
  def transformUnaryNumericOperator(function: ScSyntheticFunction,
                                    invocationInfo: InvocationInfo): StackValue = {
    val operation = NumericUnary(function.name)
    val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head

    val zero = push(DfTypes.defaultValue(function.retType.toPsiType))
    val value = transformExpression(singleThisArg.content, ResultReq.Required)

    binaryNumOp(zero, value, operation, invocationInfo.anchor)
  }

  private[SpecialSyntheticMethodsTransformation]
  def transformUnaryLogicalOperator(function: ScSyntheticFunction,
                                    invocationInfo: InvocationInfo): StackValue = {
    val singleThisArg = invocationInfo.argListsInEvaluationOrder.head.head
    val operation = LogicalUnary(function.name)
    operation match {
      case LogicalOperation.Not =>
        val value = transformExpression(singleThisArg.content, ResultReq.Required)
        if (verifyBooleanArgumentType(singleThisArg.content)) {
          not(value, invocationInfo.anchor)
        } else {
          buildUnknownCall(ResultReq.Required, Seq(value))
        }
      case _ =>
        throw new AssertionError(s"Didn't expect $operation")
    }
  }

  private def verifyBooleanArgumentType(expression: Option[ScExpression]): Boolean = expression
    .map(resolveExpressionType)
    .map(scTypeToDfType)
    .exists(_.is[DfBooleanType])
}

private object SpecialSyntheticMethodsTransformation {
  private type TransformF = ScalaDfaControlFlowBuilder => (ScSyntheticFunction, InvocationInfo) => StackValue
  private def sig(args: Int)(classes: IterableOnce[String], functionNames: IterableOnce[String], target: TransformF): Iterator[((Int, String, String), TransformF)] =
    classes.iterator.flatMap(clazz => functionNames.iterator.map(f => ((args, clazz, f), target)))
  private val binary = sig(2) _
  private val unary = sig(1) _

  private val syntheticTransformations: Map[(Int, String, String), TransformF] = Map.from(
    binary(NumericPrimitives, NumericBinary.keys, _.transformBinaryNumericOperator) ++
    binary(Seq(ScalaBoolean), RelationalBinary.keys, _.transformBinaryBooleanOperator) ++
    binary(Seq(ScalaBoolean), LogicalBinary.keys, _.transformBinaryLogicalOperator) ++
    unary(NumericPrimitives, NumericUnary.keys, _.transformUnaryNumericOperator) ++
    unary(Seq(ScalaBoolean), LogicalUnary.keys, _.transformUnaryLogicalOperator)
  )
}
