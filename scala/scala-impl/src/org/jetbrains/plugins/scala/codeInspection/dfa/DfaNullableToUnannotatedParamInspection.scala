package org.jetbrains.plugins.scala.codeInspection.dfa

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaVisitor
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem

class DfaNullableToUnannotatedParamInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    ScalaDfaVisitor.reportingUnsatisfiedConditionsOfKind(ScalaNullAccessProblem.nullableToUnannotatedParam)(holder)
  }
}