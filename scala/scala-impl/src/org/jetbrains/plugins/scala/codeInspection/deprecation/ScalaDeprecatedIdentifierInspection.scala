package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.codeInspection.deprecation.ScalaDeprecatedIdentifierInspection._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * @author Nikolay.Tropin
 */
class ScalaDeprecatedIdentifierInspection extends AbstractInspection(id, inspectionName) {
  private val deprecatedNames = Set("then")

  private def message(deprecatedName: String) = s"Usage of $deprecatedName as identifier is deprecated. It can be used as a keyword in future versions of scala."

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case ref: ScReferenceElement if deprecatedNames.contains(ref.refName) =>
      holder.registerProblem(ref.nameId, message(ref.refName), ProblemHighlightType.LIKE_DEPRECATED, null: TextRange)
    case named: ScNamedElement if deprecatedNames.contains(named.name) =>
      holder.registerProblem(named.nameId, message(named.name), ProblemHighlightType.LIKE_DEPRECATED, null: TextRange)
  }
}

object ScalaDeprecatedIdentifierInspection {
  val id = s"ScalaDeprecatedIdentifier"
  val inspectionName = "Deprecated identifier"
}