package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.MethodEffect
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.SpecialSupportUtils.{containingScalaClass, containingScalaObject, isPsiClassCase, scalaClass}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.ProperArgument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages.Apply
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.scTypeToDfType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

object ClassesSpecialSupport {

  def findSpecialSupportForClasses(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue])
                                  (implicit factory: DfaValueFactory): Option[(Map[ScClassParameter, DfaValue], MethodEffect)] = {
    val caseClassInfo = for {
      caseClass <- findReturnedCaseClassIfFactoryApplyCall(invocationInfo)
      classParamValues <- mapArgumentValuesToClassParameters(argumentValues, caseClass.parameters)
    } yield (classParamValues, MethodEffect(factory.fromDfType(DfType.TOP), isPure = true, handledSpecially = true))

    val regularClassInfo = for {
      regularClass <- findReturnedClassIfConstructorCall(invocationInfo)
      classParamValues <- mapArgumentValuesToClassParameters(argumentValues, regularClass.parameters)
      returnType = scTypeToDfType(regularClass.`type`().getOrAny)
    } yield (classParamValues, MethodEffect(factory.fromDfType(returnType), isPure = false, handledSpecially = true))

    caseClassInfo.orElse(regularClassInfo)
  }

  private def findReturnedCaseClassIfFactoryApplyCall(invocationInfo: InvocationInfo): Option[ScClass] = for {
    invokedElement <- invocationInfo.invokedElement
    invokedName <- invokedElement.simpleName
    if invokedName == Apply
    returnedClass <- invokedElement.returnType.extractClass.flatMap(scalaClass)
    if isPsiClassCase(returnedClass) && returnedClass.parameters.size == invocationInfo.properArguments.flatten.size
    containingObject <- containingScalaObject(invokedElement.psiElement)
    if containingObject.isSynthetic && containingObject.name == returnedClass.name
  } yield returnedClass

  private def findReturnedClassIfConstructorCall(invocationInfo: InvocationInfo): Option[ScClass] = for {
    invokedElement <- invocationInfo.invokedElement
    invokedName <- invokedElement.simpleName
    containingClass <- containingScalaClass(invokedElement.psiElement)
    if containingClass.parameters.size == invocationInfo.properArguments.flatten.size
    if invokedName == containingClass.name
  } yield containingClass

  private def mapArgumentValuesToClassParameters(argumentValues: Map[Argument, DfaValue],
                                                 classParameters: Seq[ScClassParameter]): Option[Map[ScClassParameter, DfaValue]] = {
    val mappedParameters = argumentValues.filter(_._1.kind.is[ProperArgument]).map {
      case (argument, value) => argument.kind match {
        case ProperArgument(paramMapping) => classParameters
          .find(_.name == paramMapping.name)
          .map(param => param -> value)
        case _ => None
      }
    }

    if (mappedParameters.forall(_.isDefined)) Some(mappedParameters.flatten.toMap) else None
  }
}
