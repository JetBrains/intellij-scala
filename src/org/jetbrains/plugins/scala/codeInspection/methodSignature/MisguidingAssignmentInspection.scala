package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiExpression}
import codeInspection.InspectionsUtil


class MisguidingAssignmentInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Misguiding assignment"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Misguiding assignment in method with Unit return type"

  override def getID = "MisguidingAssignment"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunctionDefinition if !f.hasExplicitType &&
            f.getType(TypingContext.empty).get == UnitType =>
      assignmentIn(f).foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new QuickFix(assignment))
      }
  }

  private def assignmentIn(f: ScFunctionDefinition) =
    f.children.takeWhile(!_.isInstanceOf[PsiExpression])
            .find(_.getNode.getElementType == ScalaTokenTypes.tASSIGN)

  private class QuickFix(assignment: PsiElement) extends LocalQuickFix {
    def getName = "Remove misguiding assignment"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      assignment.prevSiblings.takeWhile(_.isInstanceOf[PsiWhiteSpace]).foreach(_.delete())
      assignment.delete()
    }
  }
}

// warning: Detected apparent refinement of Unit; are you missing an '=' sign?