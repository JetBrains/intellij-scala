package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScParenthesisedExpr, ScPrefixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.tailrec
import scala.collection.mutable

final class DoubleNegationInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case expr: ScExpression if DoubleNegationUtil.hasDoubleNegation(expr) =>
      val fix = LocalQuickFix.from(new DoubleNegationQuickFix(expr))
      holder.registerProblem(expr, ScalaInspectionBundle.message("displayname.double.negation"), fix)
    case _ =>
  }
}

final class DoubleNegationQuickFix(expr: ScExpression)
  extends PsiBasedModCommandAction[ScExpression](expr)
    with DumbAware {
  override def getFamilyName: String = ScalaInspectionBundle.message("remove.double.negation")

  override def perform(context: ActionContext, expr: ScExpression): ModCommand = {
    if (DoubleNegationUtil.hasDoubleNegation(expr)) {
      ModCommand.psiUpdate(expr, (expr: ScExpression) => {
        val newExpr = DoubleNegationUtil.removeDoubleNegation(expr)
        expr.replaceExpression(newExpr, removeParenthesis = true)
        ()
      })
    } else ModCommand.nop()
  }
}

object DoubleNegationUtil {

  def hasDoubleNegation(expr: ScExpression): Boolean = {
    if (hasNegation(expr)) {
      expr match {
        case ScPrefixExpr(_, operand) => hasNegation(operand)
        case ScInfixExpr(left, _, right) => hasNegation(left) || hasNegation(right)
        case _ => false
      }
    } else {
      expr match {
        case ScInfixExpr(left, operation, right) => operation.refName == "==" && hasNegation(left) && hasNegation(right)
        case _ => false
      }
    }
  }

  def removeDoubleNegation(expr: ScExpression): ScExpression = {
    val text: String = stripParentheses(expr) match {
      case ScPrefixExpr(_, operand) => invertedNegationText(operand)
      case infix@ScInfixExpr(left, _, right) =>
        val hasNegLeft = hasNegation(left)
        val hasNegRight = hasNegation(right)
        val hasNegInfix = hasNegation(infix)
        val builder = new mutable.StringBuilder()
        builder.append(if (hasNegLeft) invertedNegationText(left) else left.getText)
        builder.append(if (hasNegLeft && hasNegInfix && hasNegRight) " != " else " == ")
        builder.append(if (hasNegRight) invertedNegationText(right) else right.getText)
        builder.toString()
    }
    createExpressionFromText(text, expr)(expr.getManager)
  }

  @tailrec
  private def stripParentheses(expr: ScExpression): ScExpression = expr match {
    case ScParenthesisedExpr(inner) => stripParentheses(inner)
    case expr: ScExpression => expr
  }

  private def hasNegation(expr: ScExpression): Boolean = {
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case ScPrefixExpr(operation, _) => operation.refName == "!"
      case ScInfixExpr(_, operation, _) => operation.refName == "!="
      case _ => false
    }
  }

  private def invertedNegationText(expr: ScExpression): String = {
    require(hasNegation(expr))
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case ScPrefixExpr(_, operand) => operand.getText
      case ScInfixExpr(left, _, right) => left.getText + "==" + right.getText
    }
  }
}
