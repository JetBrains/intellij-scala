package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.InsertMissingEquals
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Pavel Fatin
 */

class ApparentRefinementOfResultTypeInspection extends AbstractMethodSignatureInspection(
  "ScalaApparentRefinementOfResultType", "Apparent refinement of result type; are you missing an '=' sign?") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDeclaration  => f.typeElement match {
      case Some(e @ ScCompoundTypeElement(types, Some(_))) if types.nonEmpty =>
        holder.registerProblem(e, getDisplayName, new InsertMissingEquals(f))
      case _ =>
    }
  }
}
