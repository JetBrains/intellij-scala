package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.types.{DfIntConstantType, DfReferenceType, DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.DfaValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.ScalaCollectionImmutable

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
      collectionSizeFromType(dfaValue.getDfType)
    }
  }

  private def collectionSizeFromType(dfType: DfType): Option[Int] = dfType match {
    case referenceType: DfReferenceType => referenceType.getSpecialFieldType match {
      case intConstant: DfIntConstantType => Some(intConstant.getValue.intValue)
      case _ => None
    }
    case _ => None
  }
}
