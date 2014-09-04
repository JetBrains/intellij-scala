package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddUnitTypeToDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

/**
 * Nikolay.Tropin
 * 6/24/13
 */
class DeclarationHasNoExplicitTypeInspection extends AbstractMethodSignatureInspection(
  "DeclarationHasNoExplicitType", InspectionBundle.message("declaration.has.no.explicit.type.name")) {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDeclaration if f.hasUnitResultType && !f.hasExplicitType =>
      holder.registerProblem(f.nameId, getDisplayName, new AddUnitTypeToDeclaration(f))
  }
}