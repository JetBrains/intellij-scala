package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaResult.ProblemOccurrence
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.DfaConstantValue

final class ScalaDfaResult(val collectConstantConditions: Map[ScalaDfaAnchor, DfaConstantValue],
                           val collectUnsatisfiedConditions: Map[ScalaDfaProblem, ProblemOccurrence])

object ScalaDfaResult {
  /**
   * Occurrence lattice
   *
   *   Sometimes
   *   /       \
   * Always  AssumeNot <- this means it could occur, but we are so unsure that we won't report it
   *   \       /
   *    Unknown
   */
  sealed abstract class ProblemOccurrence(val shouldReport: Boolean) {
    def join(other: ProblemOccurrence): ProblemOccurrence =
      (this, other) match {
        case (a, b) if a == b => a
        case (ProblemOccurrence.Unknown, other) => other
        case (other, ProblemOccurrence.Unknown) => other
        case _ => ProblemOccurrence.Sometimes
      }
  }

  object ProblemOccurrence {
    case object Sometimes extends ProblemOccurrence(shouldReport = true)
    case object Always extends ProblemOccurrence(shouldReport = true)
    case object AssumeNot extends ProblemOccurrence(shouldReport = false)
    case object Unknown extends ProblemOccurrence(shouldReport = false)

    def fromThreeState(state: ThreeState): ProblemOccurrence =
      state match {
        case ThreeState.YES => Always
        case ThreeState.NO => AssumeNot
        case ThreeState.UNSURE => AssumeNot
      }
  }
}
