package org.jetbrains.plugins.scala.codeInspection.implicits

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.codeInspection.implicits.DeprecatedViewBoundInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

/**
* Nikolay.Tropin
* 2014-11-18
*/
class DeprecatedViewBoundInspection extends AbstractInspection(id, description) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
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
  val description = "View bounds are deprecated"
  val fixDescription = "Replace with implicit parameters"
}
