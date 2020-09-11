package org.jetbrains.plugins.scala.codeInspection.annotations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

import scala.annotation.nowarn

/**
 * User: Dmitry.Naydanov
 * Date: 30.09.15.
 */
@nowarn("msg=" + AbstractInspection.DeprecationText)
class MultipleArgLists extends AbstractInspection {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case annotation: ScAnnotation if annotation.constructorInvocation.arguments.length > 1 =>
      holder.registerProblem(annotation, ScalaInspectionBundle.message("implementation.limitation.multiple.argument.lists"))
  }
}
