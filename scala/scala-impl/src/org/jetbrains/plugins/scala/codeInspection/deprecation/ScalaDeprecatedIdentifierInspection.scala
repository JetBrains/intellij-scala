package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.deprecation.ScalaDeprecatedIdentifierInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.KindProjectorUtil

/**
 * @author Nikolay.Tropin
 */
class ScalaDeprecatedIdentifierInspection extends AbstractRegisteredInspection {
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

object ScalaDeprecatedIdentifierInspection {
  private[deprecation] val quickFixId = "Replace with `*`-syntax"

  private def message(deprecatedName: String) =
    s"Usage of $deprecatedName as identifier is deprecated. It may be used as a keyword in future versions of scala."

  private def kindProjectorMessage(hasUpToDateVersion: Boolean): String = {
    val updateSuggestion =
      if (hasUpToDateVersion) ""
      else                    "updating kind-projector plugin and "

    "Usage of `?` placeholder is going to be deprecated. Consider " + updateSuggestion + "using `*` instead."
  }

  object DeprecatedIdentifier {
    def unapply(e: PsiElement): Option[(PsiElement, Option[LocalQuickFix], String)] = e match {
      case ref: ScReference if deprecatedNames.contains(ref.refName) =>
        val (msg, quickFix) = deprecationMessageAndQuickFix(ref, ref.refName)
        Option((ref.nameId, quickFix, msg))
      case named: ScNamedElement if deprecatedNames.contains(named.name) =>
        val (msg, quickFix) = deprecationMessageAndQuickFix(named, named.name)
        Option((named.nameId, quickFix, msg))
      case _ => None
    }
  }

  private[this] val kindProjectorDeprecatedNames = Set("?", "+?", "-?")
  private[this] val deprecatedNames = Set("then") ++ kindProjectorDeprecatedNames

  private def deprecationMessageAndQuickFix(e: PsiElement, name: String): (String, Option[LocalQuickFix]) =
    if (e.kindProjectorPluginEnabled && kindProjectorDeprecatedNames.contains(name)) {
      val questionMarkDeprecated = KindProjectorUtil.isQuestionMarkSyntaxDeprecatedFor(e)
      val msg = kindProjectorMessage(questionMarkDeprecated)
      val fix = questionMarkDeprecated.option(ReplaceWithAsteriskSyntax(e))
      (msg, fix)
    } else (message(name), None)

  private final case class ReplaceWithAsteriskSyntax(e: PsiElement)
      extends AbstractFixOnPsiElement(quickFixId, e) {

    override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit =
      element match {
        case named: ScNamedElement => named.setName(named.name.replace("?", "*"))
        case ref: ScReference      => ref.handleElementRename(ref.refName.replace("?", "*"))
      }
  }
}
