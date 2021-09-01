package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.dfTypeToReportedConstant

import scala.collection.{MapView, mutable}

class ScalaDfaListener extends DfaListener {

  private val constantConditions = mutable.Map[ScalaDfaAnchor, DfaConstantValue]()
  private val unsatisfiedConditions = mutable.Map[ScalaDfaProblem, ThreeState]()

  def collectConstantConditions: MapView[ScalaDfaAnchor, DfaConstantValue] = constantConditions.view

  def collectUnsatisfiedConditions: MapView[ScalaDfaProblem, ThreeState] = unsatisfiedConditions.view

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit = anchor match {
    case scalaAnchor: ScalaDfaAnchor => recordExpressionValue(scalaAnchor, state, value)
    case _ =>
  }

  override def onCondition(unsatisfiedCondition: UnsatisfiedConditionProblem, value: DfaValue,
                           failed: ThreeState, state: DfaMemoryState): Unit = unsatisfiedCondition match {
    case scalaProblem: ScalaDfaProblem => unsatisfiedConditions.updateWith(scalaProblem) {
      case Some(oldInfo) => Some(oldInfo.merge(failed))
      case None => Some(failed)
    }
    case _ =>
  }

  private def recordExpressionValue(anchor: ScalaDfaAnchor, state: DfaMemoryState, value: DfaValue): Unit = {
    val newValue = dfTypeToReportedConstant(state.getDfType(value))
    constantConditions.updateWith(anchor) {
      case Some(oldValue) if oldValue != newValue => Some(DfaConstantValue.Unknown)
      case _ => Some(newValue)
    }
  }
}
