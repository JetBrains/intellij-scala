package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */
final class ApparentRefinementOfResultTypeInspection extends AbstractInspection("Apparent refinement of result type; are you missing an '=' sign?") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDeclaration => f.returnTypeElement match {
      case Some(e @ ScCompoundTypeElement(types, Some(_))) if types.nonEmpty =>
        holder.registerProblem(e, getDisplayName, new quickfix.InsertMissingEquals(f))
      case _ =>
    }
  }
}
