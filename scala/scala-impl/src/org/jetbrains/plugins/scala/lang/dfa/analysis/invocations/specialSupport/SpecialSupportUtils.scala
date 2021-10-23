package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.{DfIntConstantType, DfIntegralType, DfReferenceType, DfType}
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.psi.{PsiClass, PsiElement, PsiMember}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByName
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}

object SpecialSupportUtils {

  def retrieveSingleProperArgumentValue(properArguments: List[List[Argument]],
                                        argumentValues: Map[Argument, DfaValue]): Option[DfaValue] = {
    if (properArguments.flatten.size == 1) argumentValues.get(properArguments.flatten.head) else None
  }

  def collectionSpecificSizeFromDfaValueInState(dfaValue: DfaValue, state: DfaMemoryState)
                                               (implicit factory: DfaValueFactory): Option[Int] = {
    tryRetrieveCollectionSizeDirectly(dfaValue.getDfType) match {
      case Some(size) => Some(size)
      case _ => state.getDfType(SpecialField.COLLECTION_SIZE.createValue(factory, dfaValue)) match {
        case intConstant: DfIntConstantType => Some(intConstant.getValue.intValue)
        case _ => None
      }
    }
  }

  def collectionSizeRangeFromDfaValueInState(dfaValue: DfaValue, state: DfaMemoryState)
                                            (implicit factory: DfaValueFactory): Option[LongRangeSet] = {
    tryRetrieveCollectionSizeDirectly(dfaValue.getDfType) match {
      case Some(size) => Some(LongRangeSet.point(size))
      case _ => state.getDfType(SpecialField.COLLECTION_SIZE.createValue(factory, dfaValue)) match {
        case integralType: DfIntegralType => Some(integralType.getRange)
        case _ => None
      }
    }
  }

  private def tryRetrieveCollectionSizeDirectly(dfType: DfType): Option[Int] = dfType match {
    case referenceType: DfReferenceType => referenceType.getSpecialFieldType match {
      case intConstant: DfIntConstantType => Some(intConstant.getValue.intValue)
      case _ => None
    }
    case _ => None
  }

  def scalaClass(psiClass: PsiClass): Option[ScClass] = psiClass match {
    case scalaClass: ScClass => Some(scalaClass)
    case _ => None
  }

  def isPsiClassCase(psiClass: PsiClass): Boolean = psiClass match {
    case typeDefinition: ScTypeDefinition => typeDefinition.isCase
    case _ => false
  }

  def containingScalaClass(element: PsiElement): Option[ScClass] = element match {
    case member: PsiMember => member.containingClass match {
      case scalaClass: ScClass => Some(scalaClass)
      case _ => None
    }
    case _ => None
  }

  def containingScalaObject(element: PsiElement): Option[ScObject] = element match {
    case member: PsiMember => member.containingClass match {
      case scalaObject: ScObject => Some(scalaObject)
      case _ => None
    }
    case _ => None
  }

  def implicitParametersPresent(invocationInfo: InvocationInfo): Boolean = invocationInfo.invokedElement match {
    case Some(method: ScFunction) => method.parameters.exists(_.isImplicitParameter)
    case _ => false
  }

  def byNameParametersPresent(invocationInfo: InvocationInfo): Boolean = {
    invocationInfo.argListsInEvaluationOrder.flatten.exists(_.passingMechanism == PassByName)
  }
}
