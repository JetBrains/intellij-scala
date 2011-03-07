package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._
import quickfix.AddEmptyParentheses

/**
 * Pavel Fatin
 */

class MutatorLikeMethodIsParameterless extends AbstractInspection(
  "MutatorLikeMethodIsParameterless", "Method with mutator-like name is parameterless") {

  @Language("HTML")
  override val description =
 """Methods with mutator-like name are expected to have <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The convention is that you include parentheses if the method has side effects.

<small>* Refer to Programming in Scala, 5.3 Operators are methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.hasMutatorLikeName && f.isParameterless && !f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}