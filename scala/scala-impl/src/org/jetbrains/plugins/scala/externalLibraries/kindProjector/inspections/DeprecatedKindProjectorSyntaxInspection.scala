package org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections.DeprecatedKindProjectorSyntaxInspection.DeprecatedIdentifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.project._

class DeprecatedKindProjectorSyntaxInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case DeprecatedIdentifier(e, qf, message) =>
      //noinspection ReferencePassedToNls
      holder.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, qf.toArray[LocalQuickFix]: _*)
    case _ =>
  }
}

object DeprecatedKindProjectorSyntaxInspection {
  @Nls
  private[kindProjector] val quickFixId = ScalaInspectionBundle.message("replace.with.star.syntax")

  @Nls
  private def kindProjectorMessage(hasUpToDateVersion: Boolean): String =
      if (hasUpToDateVersion) ScalaInspectionBundle.message("kind.projector.deprecated.tip")
      else ScalaInspectionBundle.message("kind.projector.deprecated.tip.with.update")

  object DeprecatedIdentifier {
    def unapply(e: PsiElement): Option[(PsiElement, Option[LocalQuickFix], String)] =
      if (!e.kindProjectorPluginEnabled) None
      else
        e match {
          case (ref: ScReference) & Parent(_: ScSimpleTypeElement)
              if kindProjectorDeprecatedNames.contains(ref.refName) =>
            val (msg, quickFix) = deprecationMessageAndQuickFix(ref)
            Option((ref.nameId, quickFix, msg))
          case _ => None
        }
  }

  private[this] val kindProjectorDeprecatedNames = Set("?", "+?", "-?")
  private def deprecationMessageAndQuickFix(
    e: PsiElement,
  ): (String, Option[LocalQuickFix]) = {
    val questionMarkDeprecated = KindProjectorUtil.isQuestionMarkSyntaxDeprecatedFor(e)
    val msg = kindProjectorMessage(questionMarkDeprecated)
    val fix = questionMarkDeprecated.option(new ReplaceWithAsteriskSyntax(e))
    (msg, fix)
  }

  private final class ReplaceWithAsteriskSyntax(e: PsiElement)
      extends AbstractFixOnPsiElement(quickFixId, e) {

    override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit =
      element match {
        case named: ScNamedElement => named.setName(named.name.replace("?", "*"))
        case ref: ScReference      => ref.handleElementRename(ref.refName.replace("?", "*"))
      }
  }
}
