package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.codeInspection.implicits.DeprecatedViewBoundInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

final class DeprecatedViewBoundInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case boundsOwner: ScTypeBoundsOwner if boundsOwner.viewBound.nonEmpty && canBeConverted(boundsOwner) =>
      holder.registerProblem(boundsOwner, description, new ConvertToImplicitParametersQuickFix(boundsOwner))
    case _ =>
  }
}

final class ConvertToImplicitParametersQuickFix(owner: ScTypeBoundsOwner)
  extends AbstractFixOnPsiElement(fixDescription, owner)
    with DumbAware {
  override protected def doApplyFix(boundOwner: ScTypeBoundsOwner)
                                   (implicit project: Project): Unit = {
    val addedParams = doConversion(boundOwner)
    runRenamingTemplate(addedParams)
  }
}

object DeprecatedViewBoundInspection {
  @Nls
  val description: String = ScalaInspectionBundle.message("displayname.view.bounds.are.deprecated")
  @Nls
  val fixDescription: String = ScalaInspectionBundle.message("replace.with.implicit.parameters")
}
