package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural

import com.intellij.codeInspection.dataFlow.interpreter.{DataFlowInterpreter, RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.psi.{PsiModifier, PsiModifierListOwner}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.MethodEffect
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.SpecialSupportUtils.{byNameParametersPresent, implicitParametersPresent}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.unknownDfaValue
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext
import org.jetbrains.plugins.scala.project.ProjectContext

object InterproceduralAnalysis {

  val InterproceduralAnalysisDepthLimit = 3

  val DeepAnalysisBodySizeLimit = 3

  def tryInterpretExternalMethod(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                 currentAnalysedMethodInfo: AnalysedMethodInfo)
                                (implicit factory: DfaValueFactory): Option[MethodEffect] = {
    invocationInfo.invokedElement match {
      case Some(InvokedElement(function: ScFunctionDefinition))
        if supportsInterproceduralAnalysis(function, invocationInfo, currentAnalysedMethodInfo) => function.body match {
        case Some(body) if invocationInfo.paramToProperArgMapping.size == invocationInfo.properArguments.flatten.size =>
          val paramValues = mapArgumentValuesToParams(invocationInfo, function, argumentValues)
          analyseExternalMethodBody(function, body, paramValues, currentAnalysedMethodInfo)
        case _ => None
      }
      case _ => None
    }
  }

  def registerParameterValues(parameterValues: Map[_ <: ScParameter, DfaValue],
                              qualifier: Option[ScalaDfaVariableDescriptor],
                              interpreter: DataFlowInterpreter, state: DfaMemoryState)
                             (implicit factory: DfaValueFactory): Unit = {
    parameterValues.foreach { case (parameter, value) =>
      val variableDescriptor = ScalaDfaVariableDescriptor(parameter, qualifier, parameter.isStable)
      val dfaVariable = factory.getVarFactory.createVariableValue(variableDescriptor)
      state.push(value)
      val assignment = new SimpleAssignmentInstruction(null, dfaVariable)
      assignment.accept(interpreter, state)
      state.pop()
    }
  }

  private def supportsInterproceduralAnalysis(function: ScFunctionDefinition, invocationInfo: InvocationInfo,
                                              currentAnalysedMethodInfo: AnalysedMethodInfo): Boolean = {
    val isInsideFinalClassOrObject = hasFinalOrPrivateModifier(function.containingClass) || function.containingClass.is[ScObject]
    val isEffectivelyFinal = hasFinalOrPrivateModifier(function) || isInsideFinalClassOrObject || function.isLocal
    val containsUnsupportedFeatures = implicitParametersPresent(invocationInfo) || byNameParametersPresent(invocationInfo)
    val isRecursionOrToDeep = function == currentAnalysedMethodInfo.method ||
      currentAnalysedMethodInfo.invocationDepth + 1 > InterproceduralAnalysisDepthLimit ||
      (currentAnalysedMethodInfo.invocationDepth + 1 > 2 && longerThanDeepBodySizeLimit(function))
    val isValOrVar = function.isVal || function.isVar
    val hasRegularBody = !function.isSynthetic && !function.isAbstractMember && function.body.isDefined

    isEffectivelyFinal && !containsUnsupportedFeatures && !isRecursionOrToDeep && !isValOrVar &&
      hasRegularBody && !isLikelyConfigurationMethodOrNamedConstant(function)
  }

  private def longerThanDeepBodySizeLimit(function: ScFunctionDefinition): Boolean = function.body match {
    case Some(block: ScBlockExpr) => block.statements.size > DeepAnalysisBodySizeLimit
    case _ => false
  }

  private def hasFinalOrPrivateModifier(element: PsiModifierListOwner): Boolean = {
    Option(element).exists(_.hasModifierPropertyScala(PsiModifier.FINAL)) ||
      Option(element).exists(_.hasModifierPropertyScala(PsiModifier.PRIVATE))
  }

  private def isLikelyConfigurationMethodOrNamedConstant(function: ScFunctionDefinition): Boolean = function.body match {
    case Some(_: ScLiteral) => true
    case _ => false
  }

  private def mapArgumentValuesToParams(invocationInfo: InvocationInfo, function: ScFunctionDefinition,
                                        argumentValues: Map[Argument, DfaValue])
                                       (implicit factory: DfaValueFactory): Map[ScParameter, DfaValue] = {
    val argumentVector = invocationInfo.properArguments.flatten.toVector
    function.parameters.zip(invocationInfo.paramToProperArgMapping).map {
      case (param, argMapping) =>
        val argValue = argMapping.flatMap(index => argumentValues.get(argumentVector(index)))
          .getOrElse(unknownDfaValue)
        param -> argValue
    }.toMap
  }

  private def analyseExternalMethodBody(method: ScFunctionDefinition, body: ScExpression,
                                        mappedParameters: Map[ScParameter, DfaValue],
                                        currentAnalysedMethodInfo: AnalysedMethodInfo)
                                       (implicit factory: DfaValueFactory): Option[MethodEffect] = {
    val newAnalysedInfo = AnalysedMethodInfo(method, currentAnalysedMethodInfo.invocationDepth + 1)
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(newAnalysedInfo, factory, body)

    val endOffset = new DeferredOffset
    implicit val context: ProjectContext = method.getProject
    controlFlowBuilder.pushTrap(new EnterFinallyTrap(nopCodeBlock, endOffset))

    controlFlowBuilder.transformExpression(body)
    val resultDestination = factory.getVarFactory.createVariableValue(MethodResultDescriptor(method))
    val flow = controlFlowBuilder.buildForExternalMethod(resultDestination, endOffset)

    val listener = new MethodResultDfaListener(resultDestination)
    val interpreter = new StandardDataFlowInterpreter(flow, listener)

    val startingState = new JvmDfaMemoryStateImpl(factory)
    registerParameterValues(mappedParameters, None, interpreter, startingState)

    if (interpreter.interpret(startingState) != RunnerResult.OK) None
    else Some(MethodEffect(factory.fromDfType(listener.collectResultValue),
      isPure = true, handledSpecially = true, handledExternally = true))
  }

  private def nopCodeBlock(implicit context: ProjectContext): ScExpression = code"()".asInstanceOf[ScExpression]
}
