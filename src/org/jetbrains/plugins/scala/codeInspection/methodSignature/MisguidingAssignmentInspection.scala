package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class MisguidingAssignmentInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Misguiding assignment"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Misguiding assignment in method with Unit return type"

  override def getID = "MisguidingAssignment"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunctionDefinition if !f.hasExplicitType && f.hasUnitReturnType =>
      f.assignment.foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new QuickFix(f))
      }
  }

  private class QuickFix(f: ScFunctionDefinition) extends LocalQuickFix {
    def getName = "Remove misguiding assignment"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      f.removeAssignment()
    }
  }
}

// warning: Detected apparent refinement of Unit; are you missing an '=' sign?