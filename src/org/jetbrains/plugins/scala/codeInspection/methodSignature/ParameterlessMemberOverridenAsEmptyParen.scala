package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import quickfix.RemoveParentheses

/**
 * Pavel Fatin
 */

class ParameterlessMemberOverridenAsEmptyParen extends AbstractMethodSignatureInspection(
  "ScalaParameterlessMemberOverridenAsEmptyParen", "Parameterless Scala member overriden as empty-paren") {

  @Language("HTML")
  override val description =
"""The recommended convention is to use a parameterless method whenever there are
no parameters and the method have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>, which says that client code
should not be affected by a decision to implement an attribute as a field or method.

In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>, as overriden method is parameterless,
the overriding method must also be declared as a method without side effects.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.isEmptyParen =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if method.isParameterless =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
        case _ =>
      }
  }
}