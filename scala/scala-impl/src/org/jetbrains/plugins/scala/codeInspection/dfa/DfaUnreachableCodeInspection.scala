package org.jetbrains.plugins.scala.codeInspection.dfa

import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaProblemReporter

final class DfaUnreachableCodeInspection
  extends DfaInspectionBase(ScalaDfaProblemReporter.reportingUnreachableCode)
