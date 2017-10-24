package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class AccessorLikeMethodIsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaAccessorLikeMethodIsEmptyParen", "Method with accessor-like name is empty-paren") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.hasQueryLikeName && f.isEmptyParen && !f.hasUnitResultType &&
        f.superMethods.isEmpty && !isScalaJSFacade(f.getContainingClass) =>
      holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
  }
}