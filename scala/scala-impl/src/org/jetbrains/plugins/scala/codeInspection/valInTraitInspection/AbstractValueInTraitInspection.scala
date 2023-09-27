package org.jetbrains.plugins.scala.codeInspection.valInTraitInspection

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

class AbstractValueInTraitInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    //todo: we should use dataflow analysis to get if it's safe to use declaration here
    case v: ScValueDeclaration if v.getParent.isInstanceOf[ScTemplateBody] =>
      v.containingClass match {
        case _: ScTrait =>
          holder.registerProblem(v, ScalaInspectionBundle.message("abstract.value.used.in.trait"))
        case _ =>
      }
    case v: ScVariableDeclaration =>
      v.containingClass match {
        case _: ScTrait =>
          holder.registerProblem(v, ScalaInspectionBundle.message("abstract.variable.used.in.trait"))
        case _ =>
      }
    case _ =>
  }
}
