package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers.CustomMethodHandler
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.codeInspection.dataFlow.{CustomMethodHandlers, DfaCallArguments, MutationSignature}
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.ClassesSpecialSupport.findSpecialSupportForClasses
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.InterproceduralAnalysis.registerParameterValues
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.CollectionsSpecialSupport.findSpecialSupportForCollections
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.OtherMethodsSpecialSupport.{CommonMethodsMapping, psiMethodFromText}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{findArgumentsPrimitiveType, unknownDfaValue}
import org.jetbrains.plugins.scala.project.ProjectContext

//noinspection UnstableApiUsage
case class MethodEffectFinder(invocationInfo: InvocationInfo)(implicit factory: DfaValueFactory) {

  def findMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                       argumentValues: Map[Argument, DfaValue],
                       qualifier: Option[ScalaDfaVariableDescriptor]): MethodEffect = {
    invocationInfo.invokedElement match {
      case None => MethodEffect(unknownDfaValue, isPure = false, handledSpecially = false)
      case Some(invokedElement) =>
        findCommonMethodEffect(invokedElement, argumentValues, stateBefore)
          .getOrElse(findScalaMethodEffect(interpreter, stateBefore, argumentValues, qualifier))
    }
  }

  private def findCommonMethodEffect(invokedElement: InvokedElement,
                                     argumentValues: Map[Argument, DfaValue],
                                     stateBefore: DfaMemoryState): Option[MethodEffect] = {
    implicit val context: ProjectContext = invokedElement.psiElement.getProject
    val commonHandler = findArgumentsPrimitiveType(argumentValues).flatMap { argumentsType =>
      invokedElement.qualifiedName
        .flatMap(CommonMethodsMapping.get(_, argumentsType))
        .flatMap(psiMethodFromText)
        .map(method => (method, CustomMethodHandlers.find(method)))
    }

    findJavaMethodEffect(commonHandler, argumentValues, stateBefore).orElse {
      invokedElement.psiElement match {
        case psiMethod: PsiMethod =>
          findJavaMethodEffect(Some((psiMethod, CustomMethodHandlers.find(psiMethod))), argumentValues, stateBefore)
        case _ => None
      }
    }
  }

  private def findJavaMethodEffect(handler: Option[(PsiMethod, CustomMethodHandler)],
                                   argumentValues: Map[Argument, DfaValue],
                                   stateBefore: DfaMemoryState): Option[MethodEffect] = handler match {
    case Some((method, handler)) if handler != null =>
      Some(findMethodEffectWithJavaCustomHandler(stateBefore, argumentValues, handler, method))
    case _ => None
  }

  private def findScalaMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                                    argumentValues: Map[Argument, DfaValue], qualifier: Option[ScalaDfaVariableDescriptor])
                                   (implicit factory: DfaValueFactory): MethodEffect = {
    val returnType = invocationInfo.invokedElement
      .map(element => element.returnInfo.toDfaType)
      .getOrElse(DfType.TOP)

    val classesEnhancement = findSpecialSupportForClasses(invocationInfo, argumentValues) match {
      case Some((classParamValues, methodEffect)) =>
        registerParameterValues(classParamValues, qualifier, interpreter, stateBefore)
        Some(methodEffect)
      case _ => None
    }
    val collectionsEnhancement = findSpecialSupportForCollections(invocationInfo, argumentValues, stateBefore)

    val enhancement = classesEnhancement.orElse(collectionsEnhancement)
    enhancement.map(enhanceReturnType(returnType, _))
      .getOrElse(MethodEffect(factory.fromDfType(returnType), isPure = false, handledSpecially = false))
  }

  private def enhanceReturnType(returnType: DfType, methodEffect: MethodEffect)
                               (implicit factory: DfaValueFactory): MethodEffect = {
    val enhancedType = methodEffect.returnValue.getDfType.meet(returnType)
    methodEffect.copy(returnValue = factory.fromDfType(enhancedType))
  }

  private def findMethodEffectWithJavaCustomHandler(stateBefore: DfaMemoryState,
                                                    argumentValues: Map[Argument, DfaValue],
                                                    handler: CustomMethodHandler, psiMethod: PsiMethod)
                                                   (implicit factory: DfaValueFactory): MethodEffect = {
    val properArgumentValues = invocationInfo.properArguments.flatten
      .map(argumentValues.getOrElse(_, unknownDfaValue))
    val thisArgumentValue = invocationInfo.thisArgument
      .flatMap(argumentValues.get).getOrElse(unknownDfaValue)

    val mutationSignature = MutationSignature.fromMethod(psiMethod)
    val fixedArgumentValues = if (psiMethod.isVarArgs && properArgumentValues.isEmpty)
      List(factory.fromDfType(DfType.TOP)) else properArgumentValues
    val dfaCallArguments = new DfaCallArguments(thisArgumentValue, fixedArgumentValues.toArray, mutationSignature)
    val dfaReturnValue = Option(handler.getMethodResultValue(dfaCallArguments, stateBefore, factory, psiMethod))

    val returnValue = dfaReturnValue.getOrElse(unknownDfaValue)
    val isPure = mutationSignature.isPure && !JavaDfaHelpers.mayLeakFromType(returnValue.getDfType)
    MethodEffect(returnValue, isPure, handledSpecially = returnValue != unknownDfaValue)
  }
}
