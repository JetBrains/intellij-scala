package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory


class BracelessUnitMethodInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Braceless method with Unit type"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method with side-effect declared as property)"

  override def getID = "BracelessUnitMethod"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.paramClauses.clauses.isEmpty &&
            f.getType(TypingContext.empty).get == UnitType =>
        holder.registerProblem(f.nameId, getDisplayName, new QuickFix(f))
  }

  private class QuickFix(f: ScFunction) extends LocalQuickFix {
    def getName = "Add braces"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      val clause = ScalaPsiElementFactory.createClauseFromText("()", f.getManager)
      f.paramClauses.addClause(clause)
    }
  }
}

// warning: Detected apparent refinement of Unit; are you missing an '=' sign?