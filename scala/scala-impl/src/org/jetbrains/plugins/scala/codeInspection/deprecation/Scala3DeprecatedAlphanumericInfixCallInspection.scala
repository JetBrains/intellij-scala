package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.deprecation.Scala3DeprecatedAlphanumericInfixCallInspection._
import org.jetbrains.plugins.scala.codeInspection.quickfix._
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInfixPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.{isBacktickedName, isOpCharacter}

final class Scala3DeprecatedAlphanumericInfixCallInspection extends AbstractRegisteredInspection {

  import Scala3DeprecatedAlphanumericInfixCallInspection.message

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] = if (element.isInScala3File) {
    def descriptor(ref: ScReference, fixes: Array[LocalQuickFix]) =
      Some(manager.createProblemDescriptor(ref, message(ref.refName), isOnTheFly, fixes, highlightType))

    element.getContext match {
      case infixExpr@ScInfixExpr(_, ref, right) if ref == element && isDeprecatedInfix(ref, right) =>
        val fixes = Array[LocalQuickFix](
          new WrapRefExprInBackticksQuickFix(ref),
          new ConvertFromInfixExpressionQuickFix(infixExpr),
        )
        descriptor(ref, fixes)
      case infixType@ScInfixTypeElement(_, ref, _) if ref == element && isDeprecatedInfix(ref) =>
        val fixes = Array[LocalQuickFix](
          new WrapStableCodeRefInBackticksQuickFix(ref),
          new ConvertFromInfixTypeQuickFix(infixType),
        )
        descriptor(ref, fixes)
      case infixPattern@ScInfixPattern(_, ref, _) if ref == element && isDeprecatedInfix(ref) =>
        val fixes = Array[LocalQuickFix](
          new WrapStableCodeRefInBackticksQuickFix(ref),
          new ConvertFromInfixPatternQuickFix(infixPattern),
        )
        descriptor(ref, fixes)
      case _ => None
    }
  } else None
}

object Scala3DeprecatedAlphanumericInfixCallInspection {
  private[deprecation] def message(name: String): String =
    ScalaInspectionBundle.message("scala3.alphanumeric.definition.is.not.declared.infix", name)

  private def isDeclaredInfix(element: PsiElement): Boolean = element match {
    case fun: ScFunction if fun.isUnapplyMethod =>
      hasInfixModifier(fun) || Option(fun.syntheticCaseClass).exists(hasInfixModifier)
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
