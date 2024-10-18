package org.jetbrains.plugins.scala.codeInspection.valInTraitInspection

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScValueOrVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

final class AbstractValueInTraitInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    //todo: we should use dataflow analysis to get if it's safe to use declaration here
    case (v: ScValueOrVariableDeclaration) & Parent(_: ScTemplateBody) =>
      v.containingClass match {
        case _: ScTrait =>
          val message =
            if (v.is[ScValueDeclaration]) ScalaInspectionBundle.message("abstract.value.used.in.trait")
            else ScalaInspectionBundle.message("abstract.variable.used.in.trait")
          holder.registerProblem(v, message)
        case _ =>
      }
    case _ =>
  }
}
