package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.ProblemsHolderExt
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddCallParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}

/**
 * Pavel Fatin
 */

class JavaMutatorMethodAccessedAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaMutatorMethodAccessedAsParameterless", "Java mutator method accessed as parameterless") {

  def actionFor(holder: ProblemsHolder) = {
    case e: ScReferenceExpression if !e.getParent.isInstanceOf[ScMethodCall] &&
            !e.getParent.isInstanceOf[ScInfixExpr] &&
            !e.getParent.isInstanceOf[ScUnderscoreSection] && e.isValid &&
      !ScFunctionType.isFunctionType(e.getType().getOrAny)(holder.typeSystem) => e.resolve() match {
        case _: ScalaPsiElement => // do nothing
        case (m: PsiMethod) if m.isMutator =>
          e.getParent match {
            case gen: ScGenericCall =>
              ScalaPsiUtil.findCall(gen) match {
                case None =>
                  holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(gen))
                case Some(mc) =>
              }
            case _ =>
              holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
          }
        case _ =>
    }
  }
}
