package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.scTypeToDfType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

case class MethodResultDescriptor(method: ScFunction) extends JvmVariableDescriptor {

  override def toString: String = "METHOD_RESULT" + method.name

  override def getDfType(qualifier: DfaVariableValue): DfType = scTypeToDfType(method.returnType.getOrAny)

  override def isStable: Boolean = true
}
