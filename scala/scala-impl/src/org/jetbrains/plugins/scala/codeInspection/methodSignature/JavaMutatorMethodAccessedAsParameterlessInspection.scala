package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddCallParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}

/**
 * Pavel Fatin
 */

class JavaMutatorMethodAccessedAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaJavaMutatorMethodAccessedAsParameterless", "Java mutator method accessed as parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case e: ScReferenceExpression if !e.getParent.isInstanceOf[ScMethodCall] &&
            !e.getParent.isInstanceOf[ScInfixExpr] &&
            !e.getParent.isInstanceOf[ScUnderscoreSection] && e.isValid &&
      !FunctionType.isFunctionType(e.getType().getOrAny) => e.resolve() match {
        case _: ScalaPsiElement => // do nothing
        case (m: PsiMethod) if m.isMutator =>
          e.getParent match {
            case gen: ScGenericCall =>
              ScalaPsiUtil.findCall(gen) match {
                case None =>
                  holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(gen))
                case Some(_) =>
              }
            case _ =>
              holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
          }
        case _ =>
    }
  }
}
