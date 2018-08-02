package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */
final class UnitMethodDefinedWithEqualsSignInspection extends AbstractInspection("Method with Unit result type defined with equals sign") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDefinition if !f.hasExplicitType && f.hasUnitResultType && !f.isConstructor =>
      f.assignment.foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new quickfix.RemoveEqualsSign(f))
      }
  }
}