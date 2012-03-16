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

class JavaAccessorMethodCalledAsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaAccessorMethodCalledAsEmptyParen", "Java accessor method called as empty-paren") {

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
