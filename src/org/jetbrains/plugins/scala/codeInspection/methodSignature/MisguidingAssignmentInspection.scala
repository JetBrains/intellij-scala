package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiExpression}
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, Unit => UnitType}


class MisguidingAssignmentInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Misguiding assignment"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Misguiding assignment in method with Unit return type"

  override def getID = "MisguidingAssignment"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunctionDefinition if !f.hasExplicitType && isUnit(f) =>
      assignmentIn(f).foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new QuickFix(assignment))
      }
  }

  private def isUnit(f: ScFunction) = f.getType(TypingContext.empty) match {
    case Success(UnitType, _) => true
    case Success(ScFunctionType(UnitType, _), _) => true
    case _ => false
  }

  private def assignmentIn(f: ScFunction) =
    f.children.toList.find(_.getNode.getElementType == ScalaTokenTypes.tASSIGN)

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