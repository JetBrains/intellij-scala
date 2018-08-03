package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * Pavel Fatin
  */
final class JavaMutatorMethodOverriddenAsParameterlessInspection extends AbstractInspection("Java mutator method overridden as parameterless") {

  import quickfix._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isParameterless && f.superMethods.headOption.exists(isMutator) =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}