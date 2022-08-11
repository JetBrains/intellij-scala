package org.jetbrains.plugins.scala.codeInspection.annotations

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
class MultipleArgLists extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case annotation: ScAnnotation if annotation.constructorInvocation.arguments.length > 1 =>
      holder.registerProblem(annotation, ScalaInspectionBundle.message("implementation.limitation.multiple.argument.lists"))
    case _ =>
  }
}
