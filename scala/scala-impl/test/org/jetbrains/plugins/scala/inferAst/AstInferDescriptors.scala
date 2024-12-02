package org.jetbrains.plugins.scala.inferAst

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaVariableValue, VariableDescriptor}

case class MarkerDescriptor(index: Int) extends VariableDescriptor {

  override def isStable: Boolean = true

  override def getDfType(qualifier: DfaVariableValue): DfType = DfType.TOP
}


case object CurrentTokenDescriptor extends VariableDescriptor {

  override def isStable: Boolean = true

  override def getDfType(qualifier: DfaVariableValue): DfType = DfType.TOP
}