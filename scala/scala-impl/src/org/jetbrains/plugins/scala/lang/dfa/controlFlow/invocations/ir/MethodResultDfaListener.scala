package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState

class MethodResultDfaListener(resultDestination: DfaValue) extends DfaListener {

  var resultValue: DfType = resultDestination.getDfType

  override def beforeAssignment(source: DfaValue, destination: DfaValue, state: DfaMemoryState, anchor: DfaAnchor): Unit = {
    if (destination == resultDestination) {
      resultValue = state.getDfType(source)
    }
  }

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit = ()

  override def onCondition(unsatisfiedCondition: UnsatisfiedConditionProblem, value: DfaValue,
                           failed: ThreeState, state: DfaMemoryState): Unit = ()
}
