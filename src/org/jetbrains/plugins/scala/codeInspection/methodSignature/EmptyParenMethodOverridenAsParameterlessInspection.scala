package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.AddEmptyParentheses
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class EmptyParenMethodOverridenAsParameterlessInspection extends AbstractMethodSignatureInspection(
  "ScalaEmptyParenMethodOverridenAsParameterless", "Empty-paren Scala method overriden as parameterless") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isParameterless =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if !method.isInCompiledFile && method.isEmptyParen =>
          holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
        case _ =>
      }
  }
}