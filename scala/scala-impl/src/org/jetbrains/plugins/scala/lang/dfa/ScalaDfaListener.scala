package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, DfaListener, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.dfTypeToReportedConstant

import scala.collection.mutable

class ScalaDfaListener extends DfaListener {

  // TODO try to improve, make immutable somehow, or an immutable view in a getter, or in some other way?
  val constantConditions: mutable.Map[ScalaDfaAnchor, DfaConstantValue] = mutable.Map[ScalaDfaAnchor, DfaConstantValue]()
  val problems: mutable.Map[ScalaDfaProblem, ThreeState] = mutable.Map[ScalaDfaProblem, ThreeState]()

  override def beforePush(args: Array[DfaValue], value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState): Unit = anchor match {
    case scalaAnchor: ScalaDfaAnchor => recordExpressionValue(scalaAnchor, state, value)
    case _ =>
  }

  private def recordExpressionValue(anchor: ScalaDfaAnchor, state: DfaMemoryState, value: DfaValue): Unit = {
    val newValue = dfTypeToReportedConstant(state.getDfType(value))
    constantConditions.updateWith(anchor) {
      case Some(oldValue) if oldValue != newValue => Some(DfaConstantValue.Unknown)
      case _ => Some(newValue)
    }
  }

  override def onCondition(problem: UnsatisfiedConditionProblem, value: DfaValue,
                           failed: ThreeState, state: DfaMemoryState): Unit = problem match {
    case scalaProblem: ScalaDfaProblem => problems.updateWith(scalaProblem) {
      case Some(oldInfo) => Some(oldInfo.merge(failed))
      case None => Some(failed)
    }
    case _ =>
  }
}
