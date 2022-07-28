package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

sealed abstract class AccessorLikeMethodInspection extends AbstractMethodSignatureInspection {

  override protected def isApplicable(function: ScFunction): Boolean =
    function.isValid &&
      function.hasQueryLikeName &&
      function.superMethods.isEmpty
}

object AccessorLikeMethodInspection {

  private[this] val JsAnyFqn = "scala.scalajs.js.Any"

  final class EmptyParentheses extends AccessorLikeMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) &&
        function.isEmptyParen &&
        !function.hasUnitResultType &&
        !Option(function.containingClass).exists(isScalaJsFacade)

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] =
      Some(new quickfix.RemoveParentheses(function))
  }

  final class UnitReturnType extends AccessorLikeMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.hasUnitResultType
  }

  private def isScalaJsFacade(clazz: PsiClass) =
    clazz.elementScope.getCachedClass(JsAnyFqn).exists {
      case scalaTrait: ScTrait => clazz.isInheritor(scalaTrait, true)
      case _ => false
    }
}