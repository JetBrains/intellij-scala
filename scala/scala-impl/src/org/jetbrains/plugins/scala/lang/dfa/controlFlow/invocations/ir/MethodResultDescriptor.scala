package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue

case class MethodResultDescriptor() extends JvmVariableDescriptor {

  override def toString: String = "RESULT"

  override def getDfType(qualifier: DfaVariableValue): DfType = DfType.TOP

  override def isStable: Boolean = true
}
