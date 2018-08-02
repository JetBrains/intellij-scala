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
final class AccessorLikeMethodIsUnitInspection extends AbstractInspection("Method with accessor-like name has Unit result type") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isValid && f.hasQueryLikeName && f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName)
  }
}