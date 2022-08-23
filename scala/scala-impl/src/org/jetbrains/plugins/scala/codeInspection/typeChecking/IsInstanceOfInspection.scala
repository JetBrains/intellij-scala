package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}

class IsInstanceOfInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case element@IsInstanceOfCall.withoutExplicitType() =>
      holder.registerProblem(element, ScalaInspectionBundle.message("missing.explicit.type.in.isinstanceof.call"))
    case _ =>
  }
}
