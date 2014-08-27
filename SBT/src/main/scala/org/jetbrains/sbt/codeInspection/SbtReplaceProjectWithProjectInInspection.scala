package org.jetbrains.sbt
package codeInspection

import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtReplaceProjectWithProjectInInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case defn: ScPatternDefinition =>
      if (defn.getContainingFile.getFileType.getName != Sbt.Name) return null

      (defn.expr, defn.bindings) match {
        case (Some(call: ScMethodCall), Seq(varPat: ScReferencePattern)) =>
          val visitor = new ScalaRecursiveElementVisitor {
            override def visitMethodCallExpression(call: ScMethodCall) = call match {
              case ScMethodCall(expr, Seq(nameLit: ScLiteral, pathElt))
                  if expr.getText == "Project" && nameLit.isString && nameLit.getValue == varPat.getText =>
                // TODO: put message into bundle
                holder.registerProblem(call, "Replace Project() with project.in()",
                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SbtReplaceProjectWithProjectInQuickFix(call))
              case _ =>
                super.visitMethodCallExpression(call)
            }
          }
          call.accept(visitor)
        case _ => // do nothing
      }
  }
}

class SbtReplaceProjectWithProjectInQuickFix(val place: ScMethodCall) extends LocalQuickFix {
  def getName = "Replace Project() with project.in()" // TODO: put in bundle

  def getFamilyName = getName

  def applyFix(project: Project, descriptor: ProblemDescriptor) = place match {
    case ScMethodCall(_, Seq(_, pathElt)) =>
      place.replace(ScalaPsiElementFactory.createExpressionFromText("project.in(" + pathElt.getText + ")", place.getManager))
    case _ => // do nothing
  }
}