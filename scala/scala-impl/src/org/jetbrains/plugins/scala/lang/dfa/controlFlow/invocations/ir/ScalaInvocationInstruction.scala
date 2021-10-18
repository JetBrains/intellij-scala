package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers.CustomMethodHandler
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction, SimpleAssignmentInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import com.intellij.codeInspection.dataFlow.{CustomMethodHandlers, DfaCallArguments, MutationSignature}
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.ClassesSpecialSupport.findSpecialSupportForClasses
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.CollectionsSpecialSupport.findSpecialSupportForCollections
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.scTypeToDfType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 *
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
//noinspection UnstableApiUsage
class ScalaInvocationInstruction(invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
                                 exceptionTransfer: Option[DfaControlTransferValue])
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
    val methodEffect = findMethodEffect(interpreter, stateBefore, argumentValues)

    if (!methodEffect.isPure) {
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

  private def unknownValue(implicit factory: DfaValueFactory): DfaValue = factory.fromDfType(DfType.TOP)

  private def collectArgumentValuesFromStack(stateBefore: DfaMemoryState): Map[Argument, DfaValue] = {
    invocationInfo.argListsInEvaluationOrder.flatten
      .reverseIterator
      .map((_, stateBefore.pop()))
      .toMap
  }

  private def findMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState, argumentValues: Map[Argument, DfaValue])
                              (implicit factory: DfaValueFactory): MethodEffect = {
    invocationInfo.invokedElement.map(_.psiElement) match {
      case Some(psiMethod: PsiMethod) => Option(CustomMethodHandlers.find(psiMethod)) match {
        case Some(handler) => findMethodEffectWithJavaCustomHandler(stateBefore,
          argumentValues, handler, psiMethod)
        case _ => findMethodEffectForScalaMethod(interpreter, stateBefore, argumentValues)
      }
      case _ => findMethodEffectForScalaMethod(interpreter, stateBefore, argumentValues)
    }
  }

  private def findMethodEffectForScalaMethod(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                                             argumentValues: Map[Argument, DfaValue])
                                            (implicit factory: DfaValueFactory): MethodEffect = {
    val returnType = invocationInfo.invokedElement
      .map(element => scTypeToDfType(element.returnType))
      .getOrElse(DfType.TOP)

    findSpecialSupportForClasses(invocationInfo, argumentValues) match {
      case Some((classParamValues, methodEffect)) =>
        assignClassParameterValues(classParamValues, interpreter, stateBefore)
        val enhancedType = methodEffect.returnValue.getDfType.meet(returnType)
        methodEffect.copy(returnValue = factory.fromDfType(enhancedType))
      case _ => findSpecialSupportForCollections(invocationInfo, argumentValues) match {
        case Some(methodEffect) => val enhancedType = methodEffect.returnValue.getDfType.meet(returnType)
          methodEffect.copy(returnValue = factory.fromDfType(enhancedType))
        case _ => MethodEffect(factory.fromDfType(returnType), isPure = false)
      }
    }
  }

  private def assignClassParameterValues(classParameterValues: Map[ScClassParameter, DfaValue],
                                         interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState)
                                        (implicit factory: DfaValueFactory): Unit = {
    classParameterValues.foreach { case (parameter, value) =>
      val dfaVariable = factory.getVarFactory.createVariableValue(ScalaDfaVariableDescriptor(parameter, parameter.isStable))
      stateBefore.push(value)
      val assignment = new SimpleAssignmentInstruction(null, dfaVariable)
      assignment.accept(interpreter, stateBefore)
    }
  }

  private def findMethodEffectWithJavaCustomHandler(stateBefore: DfaMemoryState,
                                                    argumentValues: Map[Argument, DfaValue],
                                                    handler: CustomMethodHandler, psiMethod: PsiMethod)
                                                   (implicit factory: DfaValueFactory): MethodEffect = {
    val properArgumentValues = invocationInfo.properArguments.flatten
      .map(argumentValues.getOrElse(_, unknownValue))
    val thisArgumentValue = invocationInfo.thisArgument
      .flatMap(argumentValues.get).getOrElse(unknownValue)

    val mutationSignature = MutationSignature.fromMethod(psiMethod)
    val dfaCallArguments = new DfaCallArguments(thisArgumentValue, properArgumentValues.toArray, mutationSignature)
    val dfaReturnValue = Option(handler.getMethodResultValue(dfaCallArguments, stateBefore, factory, psiMethod))

    val returnValue = dfaReturnValue.getOrElse(unknownValue)
    val isPure = mutationSignature.isPure && !JavaDfaHelpers.mayLeakFromType(returnValue.getDfType)
    MethodEffect(returnValue, isPure)
  }
}
