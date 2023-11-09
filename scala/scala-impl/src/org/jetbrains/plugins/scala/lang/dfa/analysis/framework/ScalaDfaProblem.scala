package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.{DerivedVariableDescriptor, DfaValue}
import com.intellij.util.ThreeState
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaResult.ProblemOccurrence
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

sealed trait ScalaDfaProblem extends UnsatisfiedConditionProblem {
  def problemOccurrenceWith(failed: ThreeState, value: DfaValue, state: DfaMemoryState): ProblemOccurrence =
    ProblemOccurrence.fromThreeState(failed)
  def registerTo(holder: ProblemsHolder, occurrence: ProblemOccurrence): Unit
}

object ScalaDfaProblem {
  trait WithKind extends ScalaDfaProblem {
    def problemKind: ScalaDfaProblemKind[_]
    def problemElement: ScExpression

    override def registerTo(holder: ProblemsHolder, occurrence: ProblemOccurrence): Unit = {
      @Nls
      val message = occurrence match {
        case ProblemOccurrence.Sometimes => problemKind.sometimesMessage
        case ProblemOccurrence.Always => problemKind.alwaysMessage
        case _ => return
      }
      holder.registerProblem(problemElement, message)
    }
  }
}

final case class ScalaCollectionAccessProblem(lengthDescriptor: DerivedVariableDescriptor,
                                              override val problemElement: ScExpression,
                                              override val problemKind: ScalaDfaProblemKind[ScalaCollectionAccessProblem])
  extends ScalaDfaProblem with IndexOutOfBoundsProblem with ScalaDfaProblem.WithKind {
  override def getLengthDescriptor: DerivedVariableDescriptor = lengthDescriptor
}

object ScalaCollectionAccessProblem {
  trait Factory { this: ScalaDfaProblemKind[ScalaCollectionAccessProblem] =>
    def create(problemElement: ScExpression, lengthDescriptor: DerivedVariableDescriptor): ScalaCollectionAccessProblem =
      ScalaCollectionAccessProblem(lengthDescriptor, problemElement, this)
  }
  type ProblemWithFactory = ScalaDfaProblemKind[ScalaCollectionAccessProblem] with Factory

  val indexOutOfBoundsProblem: ProblemWithFactory = new ScalaDfaProblemKind(ScalaInspectionBundle.message("invocation.index.out.of.bounds"))() with Factory
  val noSuchElementProblem: ProblemWithFactory = new ScalaDfaProblemKind(ScalaInspectionBundle.message("invocation.no.such.element"))() with Factory
}


final case class ScalaNullAccessProblem(override val problemElement: ScExpression,
                                        override val problemKind: ScalaDfaProblemKind[ScalaNullAccessProblem])
  extends ScalaDfaProblem with ScalaDfaProblem.WithKind
{
  override def problemOccurrenceWith(failed: ThreeState, value: DfaValue, state: DfaMemoryState): ProblemOccurrence =
    failed match {
      case ThreeState.UNSURE =>
        DfaNullability.fromDfType(state.getDfType(value)) match {
          case DfaNullability.NULL => ProblemOccurrence.Always
          case DfaNullability.NULLABLE => ProblemOccurrence.Sometimes
          case _ => ProblemOccurrence.AssumeNot
        }
      case failed => ProblemOccurrence.fromThreeState(failed)
    }
}

object ScalaNullAccessProblem {
  trait Factory { this: ScalaDfaProblemKind[ScalaNullAccessProblem] =>
    def create(problemElement: ScExpression): ScalaNullAccessProblem = ScalaNullAccessProblem(problemElement, this)
  }
  type ProblemWithFactory = ScalaDfaProblemKind[ScalaNullAccessProblem] with Factory

  val npeOnInvocation: ProblemWithFactory = new ScalaDfaProblemKind(ScalaBundle.message("method.invocation.might.produce.nullpointerexception"))( ScalaBundle.message("method.invocation.will.produce.nullpointerexception")) with Factory
}

class ScalaDfaProblemKind[+E <: ScalaDfaProblem.WithKind](@Nls val sometimesMessage: String)
                                                         (@Nls val alwaysMessage: String = sometimesMessage)
