package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, Instruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaAnchor

class ScalaReportReturnInstruction(val anchor: ScalaDfaAnchor) extends Instruction {

  override def accept(dataFlowInterpreter: DataFlowInterpreter, dfaMemoryState: DfaMemoryState): Array[DfaInstructionState] = {
    assert(!dfaMemoryState.isEmptyStack)
    nextStates(dataFlowInterpreter, dfaMemoryState)
  }

  override def toString: String = "REPORT_RETURN"
}
