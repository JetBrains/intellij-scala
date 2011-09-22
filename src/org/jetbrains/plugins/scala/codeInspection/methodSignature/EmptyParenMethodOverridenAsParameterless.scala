package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import quickfix.AddEmptyParentheses
import org.intellij.lang.annotations.Language

/**
 * Pavel Fatin
 */

class EmptyParenMethodOverridenAsParameterless extends AbstractMethodSignatureInspection(
  "EmptyParenMethodOverridenAsParameterless", "Emtpy-paren Scala method overriden as parameterless") {

  @Language("HTML")
  val description =
"""The convention is that you include parentheses if the method has <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>, as overriden method is empty-paren,
the overriding method must also be declared as a method with side effects.

<small>* Refer to Programming in Scala, 5.3 Operators are methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.isParameterless =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if method.isEmptyParen =>
          holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
        case _ =>
      }
  }
}