package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.extensions._
import quickfix.RemoveParentheses

/**
 * Pavel Fatin
 */

class AccessorLikeMethodIsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaAccessorLikeMethodIsEmptyParen", "Method with accessor-like name is empty-paren") {

  @Language("HTML")
  val description =
"""Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors are expected
to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The recommended convention is to use a parameterless method whenever there are
no parameters and the method have no side effect.

This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>, which says that client code
should not be affected by a decision to implement an attribute as a field or method.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.hasQueryLikeName && f.isEmptyParen && !f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
  }
}