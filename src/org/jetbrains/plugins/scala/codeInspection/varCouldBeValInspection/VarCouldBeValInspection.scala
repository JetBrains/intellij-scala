package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInspection._
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool
import com.intellij.psi.PsiElementVisitor

// This is checked in ScalaLocalVarCouldBeValPass, the inspection is to allow this to be
// turned on/off in the Inspections settings.
class VarCouldBeValInspection extends LocalInspectionTool with UnfairLocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    PsiElementVisitor.EMPTY_VISITOR
  }
}

object VarCouldBeValInspection {
  val ShortName: String = "VarCouldBeVal"

  val Annotation: String = "var could be a val"
}
