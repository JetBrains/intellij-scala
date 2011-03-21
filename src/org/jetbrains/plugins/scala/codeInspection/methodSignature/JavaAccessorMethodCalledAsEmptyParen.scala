package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import quickfix.RemoveCallParentheses

/**
 * Pavel Fatin
 */

class JavaAccessorMethodCalledAsEmptyParen extends AbstractMethodSignatureInspection(
  "JavaAccessorMethodCalledAsEmptyParen", "Java accessor method called as empty-paren") {

  @Language("HTML")
  val description =
"""Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors are expected
to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The recommended convention is to use a parameterless method whenever there are
no parameters and the method have no side effect.

This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>, which says that client code
should not be affected by a decision to implement an attribute as a field or method.

The problem is that Java does not implement the uniform access principle.

To bridge that gap, Scala allows you to leave off the empty parentheses
on an invocation of function that takes no arguments.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression => e.getParent match {
      case call: ScMethodCall if call.argumentExpressions.isEmpty => e.resolve match {
        case _: ScalaPsiElement => // do nothing
        case (m: PsiMethod) if m.isAccessor =>
          holder.registerProblem(e.nameId, getDisplayName, new RemoveCallParentheses(call))
        case _ =>
      }
      case _ =>
    }
  }
}
