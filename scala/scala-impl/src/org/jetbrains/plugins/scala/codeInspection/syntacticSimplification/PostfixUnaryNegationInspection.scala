package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.PostfixUnaryNegationInspection.{createQuickfix, isPostfixUnaryNegation}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class PostfixUnaryNegationInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean)
  : Option[ProblemDescriptor] =
    element match {
      case ref: ScReferenceExpression if isPostfixUnaryNegation(ref) =>
        super.problemDescriptor(element.getLastChild, createQuickfix(ref), descriptionTemplate, highlightType)
      case _ => None
    }
}

object PostfixUnaryNegationInspection {
  /**
   * Checks whether the expression is a postfix unary_! expression. Just unary_! is a method reference and should still
   * be allowed, however.
   * @param ref The reference expression to check.
   * @return Whether the given expression is a postfix unary negation.
   */
  private def isPostfixUnaryNegation(ref: ScReferenceExpression): Boolean =
    !ref.textMatches("unary_!") && ref.getLastChild.textMatches("unary_!")

  /**
   * Creates the quick fix for postfix unary negation references. This fix creates a new prefix negation expression and
   * replaces the old expression for it.
   * @param ref The old reference expression containing the unary_! identifier.
   * @return The quick-fix.
   */
  private def createQuickfix(ref: ScReferenceExpression) =
    Some(new PostfixUnaryNegationQuickFix(ScalaInspectionBundle.message("use.prefix.negation"), ref))

  private[syntacticSimplification] class PostfixUnaryNegationQuickFix(name: String, element: ScReferenceExpression)
    extends AbstractFixOnPsiElement[ScReferenceExpression](name, element) {

    override protected def doApplyFix(element: ScReferenceExpression)(implicit project: Project): Unit = {
      val negationExpr = ScalaPsiElementFactory.createExpressionFromText(s"!${element.getFirstChild.getText}")
      element.replaceExpression(negationExpr, removeParenthesis = true)
    }
  }
}
