package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveCallParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

/**
 * Pavel Fatin
 */

class JavaAccessorMethodCalledAsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaAccessorMethodCalledAsEmptyParen", "Java accessor method called as empty-paren") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression => e.getParent match {
      case call: ScMethodCall =>
        call.getParent match {
          case callParent: ScMethodCall => // do nothing
          case _ => if (call.argumentExpressions.isEmpty) {
            e.resolve() match {
              case _: ScalaPsiElement => // do nothing
              case (m: PsiMethod) if m.isAccessor =>
                holder.registerProblem(e.nameId, getDisplayName, new RemoveCallParentheses(call))
              case _ =>
            }
          }
        }
      case _ =>
    }
  }
}
