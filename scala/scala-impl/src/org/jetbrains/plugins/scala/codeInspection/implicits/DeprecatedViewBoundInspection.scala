package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.codeInspection.implicits.DeprecatedViewBoundInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class DeprecatedViewBoundInspection extends AbstractInspection(description) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case boundsOwner: ScTypeBoundsOwner if boundsOwner.viewBound.nonEmpty && canBeConverted(boundsOwner) =>
      holder.registerProblem(boundsOwner, description, new ConvertToImplicitParametersQuickFix(boundsOwner))
  }
}

class ConvertToImplicitParametersQuickFix(owner: ScTypeBoundsOwner) extends AbstractFixOnPsiElement(fixDescription, owner) {

  override protected def doApplyFix(boundOwner: ScTypeBoundsOwner)
                                   (implicit project: Project): Unit = {
    val addedParams = doConversion(boundOwner)
    runRenamingTemplate(addedParams)
  }
}

object DeprecatedViewBoundInspection {
  val id = "DeprecatedViewBound"
  @Nls
  val description: String = ScalaInspectionBundle.message("view.bounds.are.deprecated")
  @Nls
  val fixDescription: String = ScalaInspectionBundle.message("replace.with.implicit.parameters")
}
