package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.{ContractValue, DfaCallArguments, DfaCallState, DfaNullability, HardcodedContracts, JavaMethodContractUtil, MethodContract, MutationSignature}
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import com.intellij.psi.PsiMethod
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.InterproceduralAnalysis.tryInterpretExternalMethod
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.SpecialSupportUtils.{byNameParametersPresent, isImplicitParametersPresent}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassByValue, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.unknownDfaValue

import java.util
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 *
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
class ScalaInvocationInstruction(invocationInfo: InvocationInfo,
                                 qualifier: Option[ScalaDfaVariableDescriptor],
                                 exceptionTransfer: Option[DfaControlTransferValue],
                                 currentAnalysedMethodInfo: AnalysedMethodInfo)
  extends ExpressionPushingInstruction(invocationInfo.anchor) {

  override def toString: String = {
    val invokedElementString = invocationInfo.invokedElement
      .map(_.toString)
      .getOrElse("<unknown>")
    s"CALL $invokedElementString"
  }

  override def accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array[DfaInstructionState] = {
    implicit val factory: DfaValueFactory = interpreter.getFactory
    val argumentValues = collectArgumentValuesFromStack(stateBefore)
    checkArgumentsNullability(argumentValues, interpreter, stateBefore)

    val finder = MethodEffectFinder(invocationInfo)
    val methodEffect = finder.findMethodEffect(interpreter, stateBefore, argumentValues, qualifier)

    if (!methodEffect.isPure || byNameParametersPresent(invocationInfo) || isImplicitParametersPresent(invocationInfo)) {
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

    applyMethodContracts(argumentValues, improvedMethodEffect, stateBefore, interpreter)
      .getOrElse(returnFromInvocation(improvedMethodEffect, stateBefore, interpreter))
  }

  //noinspection UnstableApiUsage
  private def applyMethodContracts(argumentValues: Map[Argument, DfaValue],
                                   methodEffect: MethodEffect,
                                   stateBefore: DfaMemoryState,
                                   interpreter: DataFlowInterpreter)
                                  (implicit factory: DfaValueFactory): Option[Array[DfaInstructionState]] = {
    val psiMethod = invocationInfo.invokedElement.map(_.psiElement) match {
      case Some(m: PsiMethod) => m
      case _ => return None
    }
    val hardcoded = HardcodedContracts.getHardcodedContracts(psiMethod, null)
    val contracts =
      if (!hardcoded.isEmpty) hardcoded
      else if (JavaMethodContractUtil.hasExplicitContractAnnotation(psiMethod)) JavaMethodContractUtil.getMethodContracts(psiMethod)
      else return None
    if (contracts.isEmpty) return None
    // Only apply contracts that contain fail clauses (assertion/precondition methods)
    // to avoid interfering with the existing Scala DFA analysis for regular method calls
    if (!contracts.asScala.exists(_.getReturnValue.isFail)) return None

    val properArgValues = invocationInfo.properArguments.flatten
      .map(argumentValues.getOrElse(_, unknownDfaValue))
    val thisArgValue = invocationInfo.thisArgument
      .flatMap(argumentValues.get).getOrElse(unknownDfaValue)

    val mutationSignature = MutationSignature.fromMethod(psiMethod)
    val dfaCallArguments = new DfaCallArguments(thisArgValue, properArgValues.toArray, mutationSignature)
    val defaultResult = factory.fromDfType(methodEffect.returnValue.getDfType)
    val initialState = new DfaCallState(stateBefore, dfaCallArguments, defaultResult)

    val finalStates = new util.LinkedHashSet[DfaMemoryState]()
    var currentStates: util.Set[DfaCallState] = util.Collections.singleton(initialState)

    for (contract <- contracts.asScala) {
      currentStates = addContractResults(contract, currentStates, factory, finalStates)
    }

    for (callState <- currentStates.asScala) {
      callState.getMemoryState.push(defaultResult)
      finalStates.add(callState.getMemoryState)
    }

    val result = finalStates.asScala.flatMap { state =>
      ContractValue.flushContractTempVariables(state)
      val tos = state.pop()
      pushResult(interpreter, state, tos)
      val normal = nextState(interpreter, state)
      val exceptional = exceptionTransfer.map(_.dispatch(state.createCopy(), interpreter).asScala).getOrElse(Nil)
      exceptional :+ normal
    }

    Some(result.toArray)
  }

  //noinspection UnstableApiUsage
  private def addContractResults(contract: MethodContract,
                                 states: util.Set[DfaCallState],
                                 factory: DfaValueFactory,
                                 finalStates: util.Set[DfaMemoryState]): util.Set[DfaCallState] = {
    if (contract.isTrivial) {
      for (callState <- states.asScala) {
        val result = contract.getReturnValue.getDfaValue(factory, callState)
        callState.getMemoryState.push(result)
        finalStates.add(callState.getMemoryState)
      }
      return util.Collections.emptySet()
    }

    val falseStates = new util.LinkedHashSet[DfaCallState]()
    for (callState0 <- states.asScala) {
      var callState = callState0
      for (condition <- contract.getConditions.asScala) {
        callState = condition.updateState(callState)
      }
      var state: DfaMemoryState = callState.getMemoryState
      val arguments = callState.getCallArguments
      for (contractValue <- contract.getConditions.asScala if state != null) {
        val condition = contractValue.makeCondition(factory, callState.getCallArguments)
        val falseState = state.createCopy()
        val falseCondition = condition.negate()
        val falsePossible =
          if (contract.getReturnValue.isFail) falseState.applyCondition(falseCondition)
          else falseState.applyContractCondition(falseCondition)
        if (falsePossible) {
          falseStates.add(callState.withMemoryState(falseState).withArguments(arguments))
        }
        if (!state.applyContractCondition(condition)) {
          state = null
        }
      }
      if (state != null) {
        val result = contract.getReturnValue.getDfaValue(factory, callState.withArguments(arguments))
        state.push(result)
        finalStates.add(state)
      }
    }
    falseStates
  }

  private def checkArgumentsNullability(arguments: Map[Argument, DfaValue],
                                        interpreter: DataFlowInterpreter,
                                        stateBefore: DfaMemoryState): Unit =
    for {
      (argument, value) <- arguments
      if argument.nullability == Nullability.NOT_NULL ||
        argument.nullability == Nullability.UNKNOWN && invocationInfo.calledElementIsInProject
      expr <- argument.content
    } {
      val nullability = DfaNullability.fromDfType(stateBefore.getDfType(value))
      val failed = nullability match {
        case DfaNullability.NOT_NULL => ThreeState.NO
        case DfaNullability.NULL => ThreeState.YES
        case _ => ThreeState.UNSURE
      }
      val problem =
        if (argument.kind == ThisArgument) ScalaNullAccessProblem.npeOnInvocation.create(expr)
        else if (argument.nullability == Nullability.UNKNOWN) ScalaNullAccessProblem.nullableToUnannotatedParam.create(expr)
        else ScalaNullAccessProblem.nullableToNotNullParam.create(expr)
      interpreter.getListener.onCondition(problem, value, failed, stateBefore)
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
      .filter(_.passingMechanism == PassByValue)
      .map(arg => (arg, stateBefore.pop()))
      .toMap
  }

  private def evaluateArgumentsInCurrentState(argumentValues: Map[Argument, DfaValue],
                                              stateBefore: DfaMemoryState)
                                             (implicit factory: DfaValueFactory): Map[Argument, DfaValue] = {
    argumentValues.view.mapValues(value => factory.fromDfType(stateBefore.getDfType(value))).toMap
  }
}
