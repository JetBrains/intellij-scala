package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState

class MethodResultDfaListener(resultDestination: DfaValue) extends DfaListener {

  private var resultValue: Option[DfType] = None

  def collectResultValue: DfType = resultValue match {
    case DfType.TOP | DfType.BOTTOM => DfType.TOP
    case Some(other) => other
    case _ => DfType.TOP
  }

  override def beforeAssignment(source: DfaValue, destination: DfaValue, state: DfaMemoryState, anchor: DfaAnchor): Unit = {
    if (destination == resultDestination) {
      val newValue = state.getDfType(source)
      resultValue = if (resultValue.contains(DfType.BOTTOM) || newValue == DfType.BOTTOM) Some(DfType.BOTTOM)
      else resultValue.map(_.join(newValue)).orElse(Some(newValue))
    }
  }

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit = ()

  override def onCondition(unsatisfiedCondition: UnsatisfiedConditionProblem, value: DfaValue,
                           failed: ThreeState, state: DfaMemoryState): Unit = ()
}
