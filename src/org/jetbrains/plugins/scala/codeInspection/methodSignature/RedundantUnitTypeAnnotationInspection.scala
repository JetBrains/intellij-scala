package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScFunctionDeclaration}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
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
    case f: ScFunction => f.returnTypeElement.foreach { e =>
      if(e.getType(TypingContext.empty).filter(_ == UnitType).isDefined)
        holder.registerProblem(e, getDisplayName, new QuickFix(f))
    }
  }

  private class QuickFix(f: ScFunction) extends LocalQuickFix {
    def getName = "Remove redundant Unit return type annotation"

    def getFamilyName = getName

    def applyFix(project: Project, descriptor: ProblemDescriptor) {
      val e = f.returnTypeElement.get
      val first = e.prevSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOLON)
      val last = f match {
        case declaration: ScFunctionDeclaration => Some(e)
        case definition: ScFunctionDefinition =>
          e.nextSiblings.toList.find(_.getNode.getElementType == ScalaTokenTypes.tASSIGN)
       }
      f.deleteChildRange(first.getOrElse(e), last.getOrElse(e))
    }
  }
}

// warning: Detected apparent refinement of Unit; are you missing an '=' sign?