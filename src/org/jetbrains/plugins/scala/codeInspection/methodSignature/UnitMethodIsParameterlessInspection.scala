package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddEmptyParentheses
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class UnitMethodIsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodIsParameterless", "Method with Unit result type is parameterless") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.isParameterless && f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}