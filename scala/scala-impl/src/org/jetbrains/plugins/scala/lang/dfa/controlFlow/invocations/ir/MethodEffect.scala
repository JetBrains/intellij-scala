package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.value.DfaValue

final case class MethodEffect(returnValue: DfaValue, isPure: Boolean)
