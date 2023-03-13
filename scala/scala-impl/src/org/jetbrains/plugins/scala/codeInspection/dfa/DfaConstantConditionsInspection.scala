package org.jetbrains.plugins.scala.codeInspection.dfa

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaVisitor

final class DfaConstantConditionsInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaDfaVisitor(holder)
  }
}
