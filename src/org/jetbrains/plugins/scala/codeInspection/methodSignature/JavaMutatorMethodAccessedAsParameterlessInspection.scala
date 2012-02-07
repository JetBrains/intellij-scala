package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import quickfix.AddCallParentheses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScUnderscoreSection, ScInfixExpr, ScMethodCall, ScReferenceExpression}

/**
 * Pavel Fatin
 */

class JavaMutatorMethodAccessedAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaMutatorMethodAccessedAsParameterless", "Java mutator method accessed as parameterless") {

  @Language("HTML")
  override val description =
"""Methods that has mutators-like name are expected to have <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The convention is that you include empty parentheses in method call
if the method has side effects.

<small>* Refer to Programming in Scala, 5.3 Operators are methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression if !e.getParent.isInstanceOf[ScMethodCall] &&
            !e.getParent.isInstanceOf[ScInfixExpr] &&
            !e.getParent.isInstanceOf[ScUnderscoreSection] && e.isValid => e.resolve() match {
        case _: ScalaPsiElement => // do nothing
        case (m: PsiMethod) if m.isMutator =>
          holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
        case _ =>
    }
  }
}
