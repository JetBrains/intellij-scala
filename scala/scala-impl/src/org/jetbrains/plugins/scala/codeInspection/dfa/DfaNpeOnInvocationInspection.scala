package org.jetbrains.plugins.scala.codeInspection.dfa

import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaProblemReporter
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem

final class DfaNpeOnInvocationInspection
  extends DfaInspectionBase(ScalaDfaProblemReporter.reportingUnsatisfiedConditionsOfKind(ScalaNullAccessProblem.npeOnInvocation))
