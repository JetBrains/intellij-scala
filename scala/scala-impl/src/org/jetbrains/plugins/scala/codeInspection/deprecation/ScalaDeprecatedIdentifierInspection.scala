package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{
  InspectionManager,
  LocalQuickFix,
  ProblemDescriptor,
  ProblemHighlightType
}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * @author Nikolay.Tropin
 */
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
        message(ref.refName),
        ProblemHighlightType.LIKE_DEPRECATED
      )
    case named: ScNamedElement if deprecatedNames.contains(named.name) =>
      super.problemDescriptor(
        named.nameId,
        None,
        message(named.name),
        ProblemHighlightType.LIKE_DEPRECATED
      )
    case _ => None
  }
}

object ScalaDeprecatedIdentifierInspection {
  private[ScalaDeprecatedIdentifierInspection] def message(deprecatedName: String) =
    s"Usage of $deprecatedName as identifier is deprecated. It may be used as a keyword in future versions of scala."

  private[ScalaDeprecatedIdentifierInspection] val deprecatedNames = Set("then")
}
