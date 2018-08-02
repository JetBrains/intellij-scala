package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Nikolay.Tropin
 * 6/24/13
 */
final class DeclarationHasNoExplicitTypeInspection extends AbstractInspection(InspectionBundle.message("declaration.has.no.explicit.type.name")) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDeclaration if f.hasUnitResultType && !f.hasExplicitType =>
      holder.registerProblem(f.nameId, getDisplayName, new quickfix.AddUnitTypeToDeclaration(f))
  }
}