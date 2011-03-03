package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil

class UnitMethodWithoutParensInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "No parens in method with Unit return type"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method with side-effect declared as property)"

  override def getID = "UnitMethodWithoutParens"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if !f.hasParens && f.hasUnitReturnType =>
      holder.registerProblem(f.nameId, getDisplayName, new QuickFix(f))
  }

  private class QuickFix(f: ScFunction) extends LocalQuickFix {
    def getName = "Add parens"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      f.addParens()
    }
  }
}

// TODO descriptions
// TODO tests
// TODO warning: Detected apparent refinement of Unit; are you missing an '=' sign?