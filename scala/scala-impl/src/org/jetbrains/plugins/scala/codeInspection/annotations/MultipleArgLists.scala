package org.jetbrains.plugins.scala.codeInspection.annotations

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

final class MultipleArgLists extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case annotation: ScAnnotation if annotation.constructorInvocation.arguments.lengthIs > 1 =>
      holder.registerProblem(annotation, ScalaInspectionBundle.message("implementation.limitation.multiple.argument.lists"))
    case _ =>
  }
}
