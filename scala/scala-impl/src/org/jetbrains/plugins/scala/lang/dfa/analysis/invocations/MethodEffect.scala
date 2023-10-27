package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations

import com.intellij.codeInspection.dataFlow.value.DfaValue

final case class MethodEffect(returnValue: DfaValue,
                              isPure: Boolean,
                              handledSpecially: Boolean,
                              handledExternally: Boolean = false)
