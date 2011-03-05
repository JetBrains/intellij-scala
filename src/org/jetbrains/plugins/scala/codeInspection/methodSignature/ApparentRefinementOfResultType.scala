package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import quickfix.RemoveParentheses
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement

class ApparentRefinementOfResultType extends AbstractInspection(
  "ApparentRefinementOfResultType", "Apparent refinement of Unit; are you missing an '=' sign?") {

  @Language("HTML")
  val description = null

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDeclaration  => f.typeElement match {
      case Some(e: ScCompoundTypeElement) if e.refinement.isDefined =>
        holder.registerProblem(e, getDisplayName, new RemoveParentheses(f))
      case _ =>
    }
  }
}

