package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddEmptyParentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}

/**
 * Pavel Fatin
 */

class MutatorLikeMethodIsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaMutatorLikeMethodIsParameterless", "Method with mutator-like name is parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.hasMutatorLikeName && f.isParameterless && !f.hasUnitResultType
            && f.superMethods.isEmpty && !isUndescoreFunction(f) =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }

  private def isUndescoreFunction(f: ScFunction): Boolean = f match {
    case funDef: ScFunctionDefinition => funDef.body.exists(ScUnderScoreSectionUtil.isUnderscoreFunction)
    case _ => false
  }
}