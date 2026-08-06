package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

sealed abstract class EmptyParenOverrideInspection extends AbstractMethodSignatureInspection {

  override protected def isApplicable(function: ScFunction): Boolean =
    function.isEmptyParen && function.superMethods.headOption.exists(isValidSuperMethod)

  protected def isValidSuperMethod(method: PsiMethod): Boolean

  override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] =
    Some(new quickfix.RemoveParentheses(function))
}

object EmptyParenOverrideInspection {

  final class JavaAccessorMethodOverriddenAsEmptyParenInspection extends EmptyParenOverrideInspection {

    override protected def isValidSuperMethod(method: PsiMethod): Boolean =
      quickfix.isAccessor(method)
  }

  final class ParameterlessMemberOverriddenAsEmptyParenInspection extends EmptyParenOverrideInspection {

    override protected def isValidSuperMethod(method: PsiMethod): Boolean = method match {
      case method: ScFunction if !method.isInCompiledFile => method.isParameterless
      case _ => false
    }
  }

}