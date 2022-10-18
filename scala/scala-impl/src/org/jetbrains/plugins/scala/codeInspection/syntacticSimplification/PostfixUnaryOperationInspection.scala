package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.PostfixUnaryOperationInspection.{createQuickfix, isPostfixUnaryOperation}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class PostfixUnaryOperationInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case ref: ScReferenceExpression if isPostfixUnaryOperation(ref) =>
      holder.registerProblem(ref.nameId, getDisplayName, createQuickfix(ref, ref.qualifier.get, ref.refName))
    case postfix: ScPostfixExpr if isPostfixUnaryOperation(postfix) =>
      holder.registerProblem(postfix.operation, getDisplayName, createQuickfix(postfix, postfix.operand, postfix.operation.refName))
    case _ =>
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
   * @param target   The target element that will be transformed.
   * @param operand  The expression the unary operation is performed on.
   * @param operator The unary operator that is used in its 'unary_*' form.
   * @return The quick-fix.
   */
  private def createQuickfix(target: ScExpression, operand: ScExpression, operator: String): PostfixUnaryOperationQuickFix =
    new PostfixUnaryOperationQuickFix(ScalaInspectionBundle.message("unary.operation.can.use.prefix.notation"), target, operand, operator)

  private[syntacticSimplification] class PostfixUnaryOperationQuickFix(@Nls name: String,
                                                                       target: ScExpression,
                                                                       operand: ScExpression,
                                                                       operator: String)
    extends AbstractFixOnTwoPsiElements(name, target, operand) {

    override protected def doApplyFix(target: ScExpression, operand: ScExpression)(implicit project: Project): Unit = {
      val unaryExpr =
        ScalaPsiElementFactory.createExpressionFromText(
          s"${unaryOperators(operator)}${operand.getText}",
          target
        )

      target.replaceExpression(unaryExpr, removeParenthesis = true)
    }
  }

}
