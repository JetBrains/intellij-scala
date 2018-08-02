package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}

/**
 * Pavel Fatin
 */
final class MutatorLikeMethodIsParameterlessInspection extends AbstractInspection("Method with mutator-like name is parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.hasMutatorLikeName && f.isParameterless && !f.hasUnitResultType
            && f.superMethods.isEmpty && !isUndescoreFunction(f) =>
      holder.registerProblem(f.nameId, getDisplayName, new quickfix.AddEmptyParentheses(f))
  }

  private def isUndescoreFunction(f: ScFunction): Boolean = f match {
    case funDef: ScFunctionDefinition => funDef.body.exists(ScUnderScoreSectionUtil.isUnderscoreFunction)
    case _ => false
  }
}