package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.extensions._
import quickfix.AddEmptyParentheses

/**
 * Pavel Fatin
 */

class MutatorLikeMethodIsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaMutatorLikeMethodIsParameterless", "Method with mutator-like name is parameterless") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.hasMutatorLikeName && f.isParameterless && !f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}