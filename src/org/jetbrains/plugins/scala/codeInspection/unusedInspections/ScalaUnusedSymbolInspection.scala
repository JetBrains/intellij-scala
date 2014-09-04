package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.ex.UnfairLocalInspectionTool
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor

// This is checked in ScalaUnusedSymbolPass, the inspection is to allow this to be
// turned on/off in the Inspections settings.
class ScalaUnusedSymbolInspection extends LocalInspectionTool with UnfairLocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new PsiElementVisitor {}
  }
}

object ScalaUnusedSymbolInspection {
  val ShortName: String = "ScalaUnusedSymbol"
}