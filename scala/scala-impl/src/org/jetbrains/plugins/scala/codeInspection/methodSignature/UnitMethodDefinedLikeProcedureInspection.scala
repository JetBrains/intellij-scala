package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Nikolay.Tropin
 * 6/24/13
 */
final class UnitMethodDefinedLikeProcedureInspection
  extends AbstractInspection(InspectionBundle.message("unit.method.like.procedure.name")) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case funDef: ScFunctionDefinition
      if funDef.hasUnitResultType && !funDef.hasAssign && !funDef.isConstructor && IntentionAvailabilityChecker.checkInspection(this, funDef) =>
      holder.registerProblem(funDef.nameId, getDisplayName, new quickfix.InsertReturnTypeAndEquals(funDef))
  }
}
