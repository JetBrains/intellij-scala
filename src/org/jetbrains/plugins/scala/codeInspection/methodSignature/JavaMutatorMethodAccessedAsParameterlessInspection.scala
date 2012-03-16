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
