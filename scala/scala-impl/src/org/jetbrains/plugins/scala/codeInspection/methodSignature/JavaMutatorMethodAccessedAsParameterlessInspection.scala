package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType

/**
  * Pavel Fatin
  */
final class JavaMutatorMethodAccessedAsParameterlessInspection extends AbstractInspection("Java mutator method accessed as parameterless") {

  import quickfix._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case reference@ScReferenceExpression(method: PsiMethod)
      if isValid(reference) && isMutator(method) &&
        !FunctionType.isFunctionType(reference.`type`().getOrAny) =>
      val maybeElement = reference.getParent match {
        case call: ScGenericCall if ScalaPsiUtil.findCall(call).isEmpty => Some(call)
        case _: ScGenericCall => None
        case _ => Some(reference)
      }

      maybeElement.map {
        new AddCallParentheses(_)
      }.foreach {
        holder.registerProblem(reference.nameId, getDisplayName, _)
      }
  }

  private def isValid(reference: ScReferenceExpression) = reference.getParent match {
    case _: ScMethodCall |
         _: ScInfixExpr |
         _: ScUnderscoreSection => false
    case _ => reference.isValid
  }
}
