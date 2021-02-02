package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.PostfixUnaryOperationInspection.{createQuickfix, isPostfixUnaryOperation}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class PostfixUnaryOperationInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean)
  : Option[ProblemDescriptor] =
    element match {
      case ref: ScReferenceExpression if isPostfixUnaryOperation(ref) =>
        super.problemDescriptor(ref.nameId, createQuickfix(ref, ref.qualifier.get, ref.refName),
          descriptionTemplate, highlightType)
      case postfix: ScPostfixExpr if isPostfixUnaryOperation(postfix) =>
        super.problemDescriptor(postfix.operation, createQuickfix(postfix, postfix.operand, postfix.operation.refName),
          descriptionTemplate, highlightType)
      case _ => None
    }
}

object PostfixUnaryOperationInspection {
  /**
   * The desugared names of unary operator functions mapping to their sugar forms. This is used to directly extract a
   * an operator form from the desugared names.
   */
  private val unaryOperators = Map(
    "unary_!" -> "!",
    "unary_~" -> "~",
    "unary_+" -> "+",
    "unary_-" -> "-"
  )

  /**
   * Checks whether the expression is a postfix 'unary_' expression. Non-qualified references to a 'unary_' function
   * should still be allowed.
   *
   * @param ref The reference expression to check.
   * @return Whether the given expression is a postfix unary call.
   */
  private def isPostfixUnaryOperation(ref: ScReferenceExpression): Boolean =
    ref.isQualified && unaryOperators.contains(ref.refName)

  /**
   * Checks whether the postfix expression is a 'unary_' expression.
   *
   * @param postfix The postfix expression to check.
   * @return Whether the given expression is a postfix unary call.
   */
  private def isPostfixUnaryOperation(postfix: ScPostfixExpr): Boolean =
    unaryOperators.contains(postfix.operation.refName)

  /**
   * Creates the quick fix for postfix unary operations. This fix creates a new prefix operation expression and replaces
   * the old expression for it.
   *
   * @param target The target element that will be transformed.
   * @param operand The expression the unary operation is performed on.
   * @param operator The unary operator that is used in its 'unary_*' form.
   * @return The quick-fix.
   */
  private def createQuickfix(target: ScExpression, operand: ScExpression, operator: String): Option[PostfixUnaryOperationQuickFix] =
    Some(new PostfixUnaryOperationQuickFix(
      ScalaInspectionBundle.message("unary.operation.can.use.prefix.notation"),
      target,
      operand,
      operator))

  private[syntacticSimplification] class PostfixUnaryOperationQuickFix(@Nls name: String,
                                                                       target: ScExpression,
                                                                       operand: ScExpression,
                                                                       operator: String)
    extends AbstractFixOnTwoPsiElements(name, target, operand) {

    override protected def doApplyFix(target: ScExpression, operand: ScExpression)(implicit project: Project): Unit = {
      val unaryExpr = ScalaPsiElementFactory.createExpressionFromText(
        s"${unaryOperators(operator)}${operand.getText}")
      target.replaceExpression(unaryExpr, removeParenthesis = true)
    }
  }

}
