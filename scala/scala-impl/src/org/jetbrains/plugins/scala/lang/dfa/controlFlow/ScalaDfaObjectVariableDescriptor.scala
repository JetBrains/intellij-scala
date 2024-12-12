package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.{DfaVariableValue, VariableDescriptor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType

case class ScalaDfaObjectVariableDescriptor(obj: ScObject)(val outer: Option[VariableDescriptor]) extends VariableDescriptor {
  override def isStable: Boolean = true
  override def getDfType(dfaVariableValue: DfaVariableValue): DfType = {
    DfTypes.typedObject(ScalaType.designator(obj).toPsiType, Nullability.NOT_NULL)
  }
}
