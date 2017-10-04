package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix.RemoveParentheses
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class ParameterlessMemberOverridenAsEmptyParenInspection extends AbstractMethodSignatureInspection(
  "ScalaParameterlessMemberOverridenAsEmptyParen", "Parameterless Scala member overriden as empty-paren") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunction if f.isEmptyParen =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if !method.isInCompiledFile && method.isParameterless =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
        case _ =>
      }
  }
}