package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class ScalaDeprecatedIdentifierInspection extends AbstractRegisteredInspection {
  import ScalaDeprecatedIdentifierInspection._

  override protected def problemDescriptor(
    element:             PsiElement,
    maybeQuickFix:       Option[LocalQuickFix],
    descriptionTemplate: String,
    highlightType:       ProblemHighlightType
  )(implicit
    manager:    InspectionManager,
    isOnTheFly: Boolean
  ): Option[ProblemDescriptor] = element match {
    case ref: ScReference if deprecatedNames.contains(ref.refName) =>
      super.problemDescriptor(
        ref.nameId,
        None,
        ScalaInspectionBundle.message("usage.of.deprecatedname.as.identifier.is.deprecated", ref.refName),
        ProblemHighlightType.LIKE_DEPRECATED
      )
    case named: ScNamedElement if deprecatedNames.contains(named.name) =>
      super.problemDescriptor(
        named.nameId,
        None,
        ScalaInspectionBundle.message("usage.of.deprecatedname.as.identifier.is.deprecated", named.name),
        ProblemHighlightType.LIKE_DEPRECATED
      )
    case _ => None
  }
}

object ScalaDeprecatedIdentifierInspection {
  private[ScalaDeprecatedIdentifierInspection] val deprecatedNames = Set("then")
}
