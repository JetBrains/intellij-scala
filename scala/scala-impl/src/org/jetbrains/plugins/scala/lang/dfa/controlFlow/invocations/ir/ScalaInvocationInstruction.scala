package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.framework.ScalaDfaAnchor

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
class ScalaInvocationInstruction(invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
                                 exceptionTransfer: Option[DfaControlTransferValue])
  extends ExpressionPushingInstruction(invocationAnchor) {

  case class MethodEffect(returnValue: DfaValue, isPure: Boolean)

  override def toString: String = {
    val invokedElementString = invocationInfo.invokedElement
      .map(_.toString)
      .getOrElse("<unknown>")
    s"CALL $invokedElementString"
  }

  override def accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array[DfaInstructionState] = {
    val argumentValues = collectArgumentValuesFromStack(stateBefore)
    val methodEffect = findMethodReturnValue(interpreter.getFactory, stateBefore, argumentValues)

    if (!methodEffect.isPure || JavaDfaHelpers.mayLeakFromType(methodEffect.returnValue.getDfType)) {
      argumentValues.values.foreach(JavaDfaHelpers.dropLocality(_, stateBefore))
      stateBefore.flushFields()
    }

    val exceptionalState = stateBefore.createCopy()
    val exceptionalResult = exceptionTransfer.map(_.dispatch(exceptionalState, interpreter).asScala)
      .getOrElse(Nil)

    val normalResult = methodEffect.returnValue.getDfType match {
      case DfType.BOTTOM => None
      case _ => pushResult(interpreter, stateBefore, methodEffect.returnValue)
        Some(nextState(interpreter, stateBefore))
    }

    (exceptionalResult ++ normalResult).toArray
  }

  private def collectArgumentValuesFromStack(stateBefore: DfaMemoryState): Map[Argument, DfaValue] = {
    invocationInfo.argListsInEvaluationOrder.flatten
      .reverseIterator
      .map((_, stateBefore.pop()))
      .toMap
  }

  private def findMethodReturnValue(factory: DfaValueFactory, state: DfaMemoryState,
                                    value: Map[Argument, DfaValue]): MethodEffect = {
    // TODO implement
    MethodEffect(factory.fromDfType(DfType.TOP), isPure = false)
  }
}
