package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaResult.ProblemOccurrence
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.DfaConstantValue
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.dfTypeToReportedConstant

class ScalaDfaListener extends DfaListener {

  private var constantConditions = Map.empty[ScalaDfaAnchor, DfaConstantValue]
  private var unsatisfiedConditions = Map.empty[ScalaDfaProblem, ProblemOccurrence]

  def result: ScalaDfaResult = new ScalaDfaResult(constantConditions, unsatisfiedConditions)

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit =
    anchor match {
      case scalaAnchor: ScalaDfaAnchor => recordExpressionValue(scalaAnchor, state, value)
      case _ =>
    }

  override def onCondition(unsatisfiedCondition: UnsatisfiedConditionProblem,
                           value: DfaValue,
                           failed: ThreeState,
                           state: DfaMemoryState): Unit =
    unsatisfiedCondition match {
      case scalaProblem: ScalaDfaProblem =>
        val prev = unsatisfiedConditions.getOrElse(scalaProblem, ProblemOccurrence.Unknown)
        unsatisfiedConditions += scalaProblem -> prev.join(ProblemOccurrence.fromThreeState(failed))
      case _ =>
    }

  private def recordExpressionValue(anchor: ScalaDfaAnchor, state: DfaMemoryState, value: DfaValue): Unit = {
    val newValue = dfTypeToReportedConstant(state.getDfType(value))
    constantConditions += anchor -> (constantConditions.get(anchor) match {
      case Some(oldValue) if oldValue != newValue => DfaConstantValue.Other
      case _ => newValue
    })
  }
}
