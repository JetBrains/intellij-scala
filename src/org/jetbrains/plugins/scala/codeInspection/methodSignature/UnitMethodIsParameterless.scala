package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import quickfix.AddEmptyParentheses

/**
 * Pavel Fatin
 */

class UnitMethodIsParameterless extends AbstractMethodSignatureInspection(
  "UnitMethodIsParameterless", "Method with Unit result type is parameterless") {

  @Language("HTML")
  val description =
"""Methods with a result type of <code>Unit</code> are only executed for their <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

  The convention is that you include parentheses if the method has side effects.

  <small>* Refer to Programming in Scala, 5.3 Operators are methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.isParameterless && f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}