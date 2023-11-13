package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class ScalaDfaVisitor(val resultF: ScalaDfaResult => Unit) extends ScalaElementVisitor {
  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit =
    DfaManager.getDfaResultFor(function).foreach(resultF)
}

object ScalaDfaVisitor {
  def reportingTo(f: ScalaDfaProblemReporter => ScalaDfaResult => Unit)(problemsHolder: ProblemsHolder): ScalaDfaVisitor = {
    val reporter = new ScalaDfaProblemReporter(problemsHolder)
    new ScalaDfaVisitor(f(reporter))
  }

  val reportingEverythingTo: ProblemsHolder => ScalaDfaVisitor = reportingTo(_.reportEverything)
  val reportingConstantConditionsTo: ProblemsHolder => ScalaDfaVisitor = reportingTo(_.reportConstantConditions)
  val reportingAllUnsatisfiedConditionsTo: ProblemsHolder => ScalaDfaVisitor = reportingTo(r => r.reportUnsatisfiedConditionProblems(_))
  def reportingUnsatisfiedConditionsOfKind(kind: ScalaDfaProblemKind[_]): ProblemsHolder => ScalaDfaVisitor =
    reportingTo(r => r.reportUnsatisfiedConditionProblems(_, {
      case p: ScalaDfaProblem.WithKind => p.problemKind == kind
      case _ => false
    }))
  def reportingUnsatisfiedConditionsOfKind(kind: Set[ScalaDfaProblemKind[_]]): ProblemsHolder => ScalaDfaVisitor =
    reportingTo(r => r.reportUnsatisfiedConditionProblems(_, {
      case p: ScalaDfaProblem.WithKind => kind.contains(p.problemKind)
      case _ => false
    }))
}