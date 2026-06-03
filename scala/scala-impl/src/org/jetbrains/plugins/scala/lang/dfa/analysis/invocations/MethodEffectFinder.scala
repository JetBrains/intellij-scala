package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers.CustomMethodHandler
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.codeInspection.dataFlow.{CustomMethodHandlers, DfaCallArguments, MethodContract, MutationSignature}
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.ClassesSpecialSupport.findSpecialSupportForClasses
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.InterproceduralAnalysis.registerParameterValues
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.CollectionsSpecialSupport.findSpecialSupportForCollections
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.OtherMethodsSpecialSupport.{CommonMethodsMapping, psiMethodFromText}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.specialSupport.MethodEffectInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{findArgumentsPrimitiveType, unknownDfaValue}
import org.jetbrains.plugins.scala.project.ProjectContext


final case class MethodEffect(returnValue: DfaValue,
                              mutationSignature: MutationSignature,
                              contracts: Seq[MethodContract])

object MethodEffect {
  def pure(value: DfaValue): MethodEffect = MethodEffect(value, MutationSignature.pure(), Seq.empty)
  def impure(value: DfaValue): MethodEffect = MethodEffect(value, MutationSignature.unknown(), Seq.empty)
  def apply(value: DfaValue, isPure: Boolean): MethodEffect =
    if (isPure) pure(value) else impure(value)
}

//noinspection UnstableApiUsage
case class MethodEffectFinder(invocationInfo: InvocationInfo, methodEffectInfo: MethodEffectInfo)(implicit factory: DfaValueFactory) {
  private lazy val returnType = invocationInfo.invokedElement
    .map(element => element.returnInfo.toDfaType)
    .getOrElse(DfType.TOP)

  def returnValue: DfaValue = factory.fromDfType(returnType)

  def default: MethodEffect = MethodEffect(returnValue, methodEffectInfo.mutationSignature, methodEffectInfo.contracts)

  def findSpecialMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                              argumentValues: Map[Argument, DfaValue],
                              qualifier: Option[ScalaDfaVariableDescriptor]): Option[MethodEffect] = {
    invocationInfo.invokedElement.flatMap { invokedElement =>
      findCommonMethodEffect(invokedElement, argumentValues, stateBefore)
        .orElse(findScalaMethodEffect(interpreter, stateBefore, argumentValues, qualifier))
    }
  }

  private def findCommonMethodEffect(invokedElement: InvokedElement,
                                     argumentValues: Map[Argument, DfaValue],
                                     stateBefore: DfaMemoryState): Option[MethodEffect] = {
    implicit val context: ProjectContext = invokedElement.psiElement.getProject
    def findCustomMethodHandler(psiMethod: PsiMethod): Option[(PsiMethod, CustomMethodHandler)] =
      Option(CustomMethodHandlers.find(psiMethod)).map((psiMethod, _))
    def commonHandlerByPrimitive =
      findArgumentsPrimitiveType(argumentValues)
        .flatMap { argumentsType =>
          invokedElement.qualifiedName
            .flatMap(CommonMethodsMapping.get(_, argumentsType))
            .flatMap(psiMethodFromText)
            .flatMap(findCustomMethodHandler)
        }

    def commonHandlerByMethod = invokedElement.psiElement match {
      case psiMethod: PsiMethod => findCustomMethodHandler(psiMethod)
      case _ => None
    }

    commonHandlerByPrimitive
      .orElse(commonHandlerByMethod)
      .map { case (psiMethod, handler) =>
        findMethodEffectWithJavaCustomHandler(stateBefore, argumentValues, handler, psiMethod)
      }
  }

  private def findScalaMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                                    argumentValues: Map[Argument, DfaValue], qualifier: Option[ScalaDfaVariableDescriptor])
                                   (implicit factory: DfaValueFactory): Option[MethodEffect] = {
    val classesEnhancement = findSpecialSupportForClasses(invocationInfo, argumentValues) match {
      case Some((classParamValues, methodEffect)) =>
        registerParameterValues(classParamValues, qualifier, interpreter, stateBefore)
        Some(methodEffect)
      case _ => None
    }
    val collectionsEnhancement = findSpecialSupportForCollections(invocationInfo, argumentValues, stateBefore)

    val enhancement = classesEnhancement.orElse(collectionsEnhancement)
    enhancement.map(enhanceReturnType(returnType, _))
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

    val mutationSignature = methodEffectInfo.mutationSignature
    val fixedArgumentValues = if (psiMethod.isVarArgs && properArgumentValues.isEmpty)
      List(factory.fromDfType(DfType.TOP)) else properArgumentValues
    val dfaCallArguments = new DfaCallArguments(thisArgumentValue, fixedArgumentValues.toArray, mutationSignature)
    val dfaReturnValue = Option(handler.getMethodResultValue(dfaCallArguments, stateBefore, factory, psiMethod))

    val returnValue = dfaReturnValue.getOrElse(this.returnValue)
    MethodEffect(returnValue, mutationSignature, contracts = methodEffectInfo.contracts)
  }
}
