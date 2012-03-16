package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import quickfix.RemoveEqualsSign

/**
 * Pavel Fatin
 */

class UnitMethodDefinedWithEqualsSignInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDefinedWithEqualsSign", "Method with Unit result type defined with equals sign") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDefinition if !f.hasExplicitType && f.hasUnitResultType && !f.isSecondaryConstructor =>
      f.assignment.foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new RemoveEqualsSign(f))
      }
  }
}