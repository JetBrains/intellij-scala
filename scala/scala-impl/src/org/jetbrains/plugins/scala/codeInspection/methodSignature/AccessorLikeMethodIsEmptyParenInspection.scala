package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */
final class AccessorLikeMethodIsEmptyParenInspection extends AbstractInspection("Method with accessor-like name is empty-paren") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.hasQueryLikeName && f.isEmptyParen && !f.hasUnitResultType &&
        f.superMethods.isEmpty && !isScalaJSFacade(f.getContainingClass) =>
      holder.registerProblem(f.nameId, getDisplayName, new quickfix.RemoveParentheses(f))
  }
}