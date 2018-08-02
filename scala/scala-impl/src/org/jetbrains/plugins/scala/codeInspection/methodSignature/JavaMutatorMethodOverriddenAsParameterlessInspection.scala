package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */
final class JavaMutatorMethodOverriddenAsParameterlessInspection extends AbstractInspection("Java mutator method overridden as parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isParameterless =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isMutator =>
          holder.registerProblem(f.nameId, getDisplayName, new quickfix.AddEmptyParentheses(f))
        case _ =>
      }
  }
}