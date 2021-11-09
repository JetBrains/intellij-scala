package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.InterproceduralAnalysis.tryInterpretExternalMethod
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.SpecialSupportUtils.{byNameParametersPresent, implicitParametersPresent}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 *
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
class ScalaInvocationInstruction(invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
                                 qualifier: Option[ScalaDfaVariableDescriptor],
                                 exceptionTransfer: Option[DfaControlTransferValue],
                                 currentAnalysedMethodInfo: AnalysedMethodInfo)
  extends ExpressionPushingInstruction(invocationAnchor) {

  override def toString: String = {
    val invokedElementString = invocationInfo.invokedElement
      .map(_.toString)
      .getOrElse("<unknown>")
    s"CALL $invokedElementString"
  }

  override def accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array[DfaInstructionState] = {
    implicit val factory: DfaValueFactory = interpreter.getFactory
    val argumentValues = collectArgumentValuesFromStack(stateBefore)

    val finder = MethodEffectFinder(invocationInfo)
    val methodEffect = finder.findMethodEffect(interpreter, stateBefore, argumentValues, qualifier)

    if (!methodEffect.isPure || byNameParametersPresent(invocationInfo) || implicitParametersPresent(invocationInfo)) {
      argumentValues.values.foreach(JavaDfaHelpers.dropLocality(_, stateBefore))
      stateBefore.flushFields()
    }

    val improvedMethodEffect = if (!methodEffect.handledSpecially) {
      tryInterpretExternalMethod(invocationInfo, evaluateArgumentsInCurrentState(argumentValues, stateBefore),
        currentAnalysedMethodInfo) match {
        case Some(externalMethodEffect) => externalMethodEffect
        case _ => methodEffect
      }
    } else methodEffect

    returnFromInvocation(improvedMethodEffect, stateBefore, interpreter)
  }

  private def returnFromInvocation(methodEffect: MethodEffect, stateBefore: DfaMemoryState,
                                   interpreter: DataFlowInterpreter): Array[DfaInstructionState] = {
    val exceptionalState = stateBefore.createCopy()
    val exceptionalResult = if (methodEffect.handledExternally) Nil
    else exceptionTransfer.map(_.dispatch(exceptionalState, interpreter).asScala).getOrElse(Nil)

    val normalResult = methodEffect.returnValue match {
      case DfType.BOTTOM => None
      case _ => pushResult(interpreter, stateBefore, methodEffect.returnValue)
        Some(nextState(interpreter, stateBefore))
    }

    (exceptionalResult ++ normalResult).toArray
  }

  private def collectArgumentValuesFromStack(stateBefore: DfaMemoryState)
                                            (implicit factory: DfaValueFactory): Map[Argument, DfaValue] = {
    invocationInfo.argListsInEvaluationOrder.flatten
      .reverseIterator
      .map(arg => (arg, popValueFromStack(stateBefore)))
      .toMap
  }

  private def popValueFromStack(stateBefore: DfaMemoryState)(implicit factory: DfaValueFactory): DfaValue = {
    if (stateBefore.isEmptyStack) factory.fromDfType(DfType.TOP) else stateBefore.pop()
  }

  private def evaluateArgumentsInCurrentState(argumentValues: Map[Argument, DfaValue],
                                              stateBefore: DfaMemoryState)
                                             (implicit factory: DfaValueFactory): Map[Argument, DfaValue] = {
    argumentValues.view.mapValues(value => factory.fromDfType(stateBefore.getDfType(value))).toMap
  }
}
