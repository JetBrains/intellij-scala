package org.jetbrains.plugins.scala.lang.dfa.cfg

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaVariableValue, VariableDescriptor}
import com.intellij.psi.PsiNamedElement

case class ScalaVariableDescriptor(variable: PsiNamedElement, override val isStable: Boolean) extends VariableDescriptor {

  // TODO implement mapping from ScType to DfType, then use variable.`type`()
  override def getDfType(qualifier: DfaVariableValue): DfType = DfType.TOP
}
