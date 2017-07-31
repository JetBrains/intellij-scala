package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveEqualsSign
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * Pavel Fatin
 */

class UnitMethodDefinedWithEqualsSignInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDefinedWithEqualsSign", "Method with Unit result type defined with equals sign") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDefinition if !f.hasExplicitType && f.hasUnitResultType && !f.isSecondaryConstructor =>
      f.assignment.foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new RemoveEqualsSign(f))
      }
  }
}