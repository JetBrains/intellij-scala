package org.jetbrains.plugins.scala.codeInspection.dfa

import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaProblemReporter

final class DfaConstantConditionsInspection
  extends DfaInspectionBase(ScalaDfaProblemReporter.reportingConstantConditions)
