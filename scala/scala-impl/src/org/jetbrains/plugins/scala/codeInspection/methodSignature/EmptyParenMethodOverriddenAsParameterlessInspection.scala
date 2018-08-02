package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */
final class EmptyParenMethodOverriddenAsParameterlessInspection extends AbstractInspection("Empty-paren Scala method overridden as parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isParameterless =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if !method.isInCompiledFile && method.isEmptyParen =>
          holder.registerProblem(f.nameId, getDisplayName, new quickfix.AddEmptyParentheses(f))
        case _ =>
      }
  }
}