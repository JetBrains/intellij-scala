package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState

class DummyDfaListener extends DfaListener {

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit = ()

  override def onCondition(unsatisfiedCondition: UnsatisfiedConditionProblem, value: DfaValue,
                           failed: ThreeState, state: DfaMemoryState): Unit = ()

  override def beforeAssignment(source: DfaValue, dest: DfaValue, state: DfaMemoryState, anchor: DfaAnchor): Unit = ()
}
