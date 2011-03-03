package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil


class RedundantUnitTypeAnnotationInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Redundant Unit return type annotation"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Redundant Unit return type annotation in method signature"

  override def getID = "RedundantUnitType"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasUnitReturnType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new QuickFix(f))
      }
  }

  private class QuickFix(f: ScFunction) extends LocalQuickFix {
    def getName = "Remove redundant Unit return type annotation"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      f.removeExplicitType()
      f match {
        case definition: ScFunctionDefinition if definition.hasAssign =>
          definition.removeAssignment()
        case _ =>
      }
    }
  }
}

// warning: Detected apparent refinement of Unit; are you missing an '=' sign?