package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class ScalaDeprecatedIdentifierInspection extends LocalInspectionTool {
  import ScalaDeprecatedIdentifierInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case ref: ScReference if deprecatedNames.contains(ref.refName) =>
      holder.registerProblem(
        ref.nameId,
        ScalaInspectionBundle.message("usage.of.deprecatedname.as.identifier.is.deprecated", ref.refName),
      )
    case named: ScNamedElement if deprecatedNames.contains(named.name) =>
      holder.registerProblem(
        named.nameId,
        ScalaInspectionBundle.message("usage.of.deprecatedname.as.identifier.is.deprecated", named.name),
      )
    case _ =>
  }
}

object ScalaDeprecatedIdentifierInspection {
  private[ScalaDeprecatedIdentifierInspection] val deprecatedNames = Set("then")
}
