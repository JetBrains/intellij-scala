package org.jetbrains.sbt
package codeInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtReplaceProjectWithProjectInInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case defn: ScPatternDefinition if defn.getContainingFile.getFileType.getName == Sbt.Name =>
      (defn.expr, defn.bindings) match {
        case (Some(call: ScMethodCall), Seq(projectNamePattern: ScReferencePattern)) =>
          findPlaceToFix(call, projectNamePattern.getText).foreach { place =>
            holder.registerProblem(place, SbtBundle("sbt.inspection.projectIn.name"),
                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                          new SbtReplaceProjectWithProjectInQuickFix(place))
          }
        case _ => // do nothing
      }
  }

  private def findPlaceToFix(call: ScMethodCall, projectName: String): Option[ScMethodCall] = {
    var placeToFix: Option[ScMethodCall] = None
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitMethodCallExpression(call: ScMethodCall) = call match {
        case ScMethodCall(expr, Seq(nameLit: ScLiteralImpl, _))
          if expr.getText == "Project" && nameLit.stringValue.fold(false)(_ == projectName) =>
            placeToFix = Some(call)
        case _ =>
          super.visitMethodCallExpression(call)
      }
    }
    call.accept(visitor)
    placeToFix
  }
}

class SbtReplaceProjectWithProjectInQuickFix(val place: ScMethodCall) extends LocalQuickFix {
  def getName = SbtBundle("sbt.inspection.projectIn.name")

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) = place match {
    case ScMethodCall(_, Seq(_, pathElt)) =>
      place.replace(ScalaPsiElementFactory.createExpressionFromText("project.in(" + pathElt.getText + ")", place.getManager))
    case _ => // do nothing
  }
}