package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.deprecation.DeprecatedKindProjectorSyntaxInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.KindProjectorUtil

class DeprecatedKindProjectorSyntaxInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(
    element:             PsiElement,
    maybeQuickFix:       Option[LocalQuickFix],
    descriptionTemplate: String,
    highlightType:       ProblemHighlightType
  )(implicit
    manager:    InspectionManager,
    isOnTheFly: Boolean
  ): Option[ProblemDescriptor] = element match {
    case DeprecatedIdentifier(e, qf, message) =>
      super.problemDescriptor(e, qf, message, ProblemHighlightType.LIKE_DEPRECATED)
    case _ => None
  }
}

object DeprecatedKindProjectorSyntaxInspection {
  private[deprecation] val quickFixId = "Replace with `*`-syntax"

  private def kindProjectorMessage(hasUpToDateVersion: Boolean): String = {
    val updateSuggestion =
      if (hasUpToDateVersion) ""
      else                    "updating kind-projector plugin and "

    "Usage of `?` placeholder is going to be deprecated. Consider " + updateSuggestion + "using `*` instead."
  }

  object DeprecatedIdentifier {
    def unapply(e: PsiElement): Option[(PsiElement, Option[LocalQuickFix], String)] =
      if (!e.kindProjectorPluginEnabled) None
      else
        e match {
          case (ref: ScReference) && Parent(_: ScSimpleTypeElement)
              if kindProjectorDeprecatedNames.contains(ref.refName) =>
            val (msg, quickFix) = deprecationMessageAndQuickFix(ref, ref.refName)
            Option((ref.nameId, quickFix, msg))
          case _ => None
        }
  }

  private[this] val kindProjectorDeprecatedNames = Set("?", "+?", "-?")
  private def deprecationMessageAndQuickFix(
    e:    PsiElement,
    name: String
  ): (String, Option[LocalQuickFix]) = {
    val questionMarkDeprecated = KindProjectorUtil.isQuestionMarkSyntaxDeprecatedFor(e)
    val msg = kindProjectorMessage(questionMarkDeprecated)
    val fix = questionMarkDeprecated.option(ReplaceWithAsteriskSyntax(e))
    (msg, fix)
  }

  private final case class ReplaceWithAsteriskSyntax(e: PsiElement)
      extends AbstractFixOnPsiElement(quickFixId, e) {

    override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit =
      element match {
        case named: ScNamedElement => named.setName(named.name.replace("?", "*"))
        case ref: ScReference      => ref.handleElementRename(ref.refName.replace("?", "*"))
      }
  }
}
