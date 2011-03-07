package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import quickfix.AddCallParentheses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScReferenceExpression}

/**
 * Pavel Fatin
 */

class EmptyParenMethodAccessedAsParameterless extends AbstractInspection(
  "EmptyParenMethodAccessedAsParameterless", "Empty-paren method accessed as parameterless") {

  @Language("HTML")
  val description =
"""The convention is that method includes parentheses if it has <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

While it's possible to leave out empty parentheses in method calls (to adapt
the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a> to Java), it's recommended to still write the empty parentheses
when the invoked method represents more than a property of its receiver object.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression if !e.getParent.isInstanceOf[ScMethodCall] &&
            !e.getParent.isInstanceOf[ScInfixExpr]=> e.resolve match {
      case (f: ScFunction) if f.isEmptyParen =>
        holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
      case _ =>
    }
  }
}