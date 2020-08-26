package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}

sealed abstract class ParameterlessOverrideInspection extends AbstractMethodSignatureInspection {

  override protected def isApplicable(function: ScFunction): Boolean =
    function.isParameterless && superMethodsAreValid(function.superMethods)

  protected def superMethodsAreValid(methods: Iterable[PsiMethod]): Boolean

  override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] =
    Some(new quickfix.AddEmptyParentheses(function))
}

object ParameterlessOverrideInspection {

  import quickfix._

  final class EmptyParenMethod extends ParameterlessOverrideInspection {

    override protected def superMethodsAreValid(methods: Iterable[PsiMethod]): Boolean =
      methods.headOption.exists {
        case function: ScFunction if !function.isInCompiledFile => function.isEmptyParen
        case _ => false
      }
  }

  final class JavaMutator extends ParameterlessOverrideInspection {

    override protected def superMethodsAreValid(methods: Iterable[PsiMethod]): Boolean =
      methods.headOption.exists(isMutator)
  }

  final class MutatorLikeMethod extends ParameterlessOverrideInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) &&
        !function.hasUnitResultType &&  // when the type is Unit, UnitMethodInspection#Parameterless is applicable
        hasMutatorLikeName(function) &&
        !isUnderscoreFunction(function)

    override protected def superMethodsAreValid(methods: Iterable[PsiMethod]): Boolean = methods.isEmpty

    private def isUnderscoreFunction(function: ScFunction): Boolean = function match {
      case ScFunctionDefinition.withBody(body) => ScUnderScoreSectionUtil.isUnderscoreFunction(body)
      case _ => false
    }
  }

}