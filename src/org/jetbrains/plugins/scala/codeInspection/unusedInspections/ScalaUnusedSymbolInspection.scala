package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.{ProblemsHolder, LocalInspectionTool}
import com.intellij.psi.PsiElementVisitor
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool

// This is checked in ScalaUnusedSymbolPass, the inspection is to allow this to be
// turned on/off in the Inspections settings.
class ScalaUnusedSymbolInspection extends LocalInspectionTool with UnfairLocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Unused Symbol"

  def getShortName: String = ScalaUnusedSymbolInspection.ShortName

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Detects local symbols that are not used"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new PsiElementVisitor {}
  }
}

object ScalaUnusedSymbolInspection {
  val ShortName: String = "ScalaUnusedSymbol"
}