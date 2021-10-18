package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.types.{DfIntConstantType, DfReferenceType, DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.psi.{PsiClass, PsiElement, PsiMember}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}

object SpecialSupportUtils {

  def retrieveSingleProperArgumentValue(properArguments: List[List[Argument]],
                                        argumentValues: Map[Argument, DfaValue]): Option[DfaValue] = {
    if (properArguments.flatten.size == 1)
      argumentValues.get(properArguments.flatten.head)
    else None
  }

  def retrieveListSize(dfaValue: DfaValue): Option[Int] = {
    if (dfaValue.getDfType.toString == s"$ScalaCollectionImmutable.Nil$$") {
      Some(DfTypes.intValue(0).getValue.toInt)
    } else {
      collectionSizeFromDfType(dfaValue.getDfType)
    }
  }

  private def collectionSizeFromDfType(dfType: DfType): Option[Int] = dfType match {
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
}
