package org.jetbrains.plugins.scala
package codeInspection
package valInTraitInspection

import lang.psi.api.statements.{ScVariableDeclaration, ScValueDeclaration}
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.ScTrait
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class AbstractValueInTraitInspection
        extends AbstractInspection("ScalaAbstractValueInTrait", "Abstract Value in Trait") {
  val description =
    """Abstract values and variables in trait may cause errors during initialization."""

  def actionFor(holder: ProblemsHolder) = {
    //todo: we should use dataflow analysis to get if it's safe to use declaration here
    case v: ScValueDeclaration if v.getParent.isInstanceOf[ScTemplateBody] =>
      v.getContainingClass match {
        case t: ScTrait =>
          holder.registerProblem(v, "Abstract value used in trait", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _ =>
      }
    case v: ScVariableDeclaration =>
      v.getContainingClass match {
        case t: ScTrait =>
          holder.registerProblem(v, "Abstract variable used in trait", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _ =>
      }
  }
}