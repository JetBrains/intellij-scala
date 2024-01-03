package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.deprecation.Scala3DeprecatedAlphanumericInfixCallInspection.isDeprecatedInfix
import org.jetbrains.plugins.scala.codeInspection.quickfix._
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInfixPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.{isBacktickedName, isOpCharacter}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

final class Scala3DeprecatedAlphanumericInfixCallInspection extends LocalInspectionTool {

  import Scala3DeprecatedAlphanumericInfixCallInspection.message

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
      if (element.features.warnAboutDeprecatedInfixCallsEnabled) {
        element.getContext match {
          case infixExpr@ScInfixExpr(_, ref, right) if ref == element && isDeprecatedInfix(ref, right) =>
            val fixes = Array[LocalQuickFix](
              new WrapRefExprInBackticksQuickFix(ref),
              new ConvertFromInfixExpressionQuickFix(infixExpr),
            )
            holder.registerProblem(ref, message(ref.refName), fixes: _*)
          case infixType@ScInfixTypeElement(_, ref, _) if ref == element && isDeprecatedInfix(ref) =>
            val fixes = Array[LocalQuickFix](
              new WrapStableCodeRefInBackticksQuickFix(ref),
              new ConvertFromInfixTypeQuickFix(infixType),
            )
            holder.registerProblem(ref, message(ref.refName), fixes: _*)
          case infixPattern@ScInfixPattern(_, ref, _) if ref == element && isDeprecatedInfix(ref) =>
            val fixes = Array[LocalQuickFix](
              new WrapStableCodeRefInBackticksQuickFix(ref),
              new ConvertFromInfixPatternQuickFix(infixPattern),
            )
            holder.registerProblem(ref, message(ref.refName), fixes: _*)
          case _ =>
        }
      }
  }
}

object Scala3DeprecatedAlphanumericInfixCallInspection {
  private[deprecation] def message(name: String): String =
    ScalaInspectionBundle.message("scala3.alphanumeric.definition.is.not.declared.infix", name)

  private def isDeclaredInfix(element: PsiElement): Boolean = element match {
    case fun: ScFunction if fun.isUnapplyMethod =>
      hasInfixModifier(fun) || fun.syntheticCaseClass.exists(hasInfixModifier)
    case modListOwner: ScModifierListOwner => hasInfixModifier(modListOwner)
    case _ => false
  }

  private def hasInfixModifier(modListOwner: ScModifierListOwner): Boolean =
    modListOwner.hasModifierPropertyScala(ScalaKeyword.INFIX)

  private def infixOKSinceFollowedBy(expr: ScExpression): Boolean = expr.is[ScBlockExpr, ScMatch]

  private def isDeprecatedInfix(ref: ScReference): Boolean = ref.refName match {
    case isBacktickedName(_) => false
    case name if !name.exists(isOpCharacter) =>
      val resolved = ref.resolve()
      resolved != null && !isDeclaredInfix(resolved) && resolved.isInScala3File
    case _ => false
  }

  private def isDeprecatedInfix(ref: ScReferenceExpression, right: ScExpression): Boolean =
    isDeprecatedInfix(ref) && !infixOKSinceFollowedBy(right)
}
