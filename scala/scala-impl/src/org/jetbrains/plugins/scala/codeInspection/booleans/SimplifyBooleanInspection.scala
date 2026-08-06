package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScTypeExt, api}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.project.ProjectContext

final class SimplifyBooleanInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case _: ScParenthesisedExpr => //do nothing to avoid many similar expressions
    case expr: ScExpression if SimplifyBooleanUtil.canBeSimplified(expr) =>
      val fix = LocalQuickFix.from(new SimplifyBooleanQuickFix(expr))
      holder.registerProblem(expr, ScalaInspectionBundle.message("displayname.simplify.boolean.expression"), fix)
    case _ =>
  }
}

final class SimplifyBooleanQuickFix(expr: ScExpression) extends PsiBasedModCommandAction[ScExpression](expr) {
  override def getFamilyName: String = ScalaInspectionBundle.message("simplify.with.text", getShortText(expr))

  override def perform(context: ActionContext, expr: ScExpression): ModCommand = {
    if (SimplifyBooleanUtil.canBeSimplified(expr)) {
      ModCommand.psiUpdate(expr, (expr: ScExpression) => {
        val simplified = SimplifyBooleanUtil.simplify(expr)
        expr.replaceExpression(simplified, removeParenthesis = true)
        ()
      })
    } else ModCommand.nop()
  }
}

object SimplifyBooleanUtil {
  private val boolInfixOperations = Set("==", "!=", "&&", "&", "||", "|", "^")

  def canBeSimplified(expr: ScExpression, isTopLevel: Boolean = true): Boolean = {
    expr match {
      case _: ScLiteral if !isTopLevel => booleanConst(expr).isDefined
      case ScParenthesisedExpr(e) => canBeSimplified(e, isTopLevel)
      case expression: ScExpression =>
        val children = getScExprChildren(expr)
        isBooleanOperation(expression) && isOfBooleanType(expr) && children.exists(canBeSimplified(_, isTopLevel = false))
    }
  }

  def simplify(expr: ScExpression, isTopLevel: Boolean = true): ScExpression = {
    if (canBeSimplified(expr, isTopLevel) && booleanConst(expr).isEmpty) {
      val exprCopy = createExpressionWithContextFromText(expr.getText, expr.getContext, expr)
      val children = getScExprChildren(exprCopy)
      children.foreach(child => exprCopy.getNode.replaceChild(child.getNode, simplify(child, isTopLevel = false).getNode))
      simplifyTrivially(exprCopy)
    }
    else expr
  }

  def isBooleanOperation(expression: ScExpression): Boolean = expression match {
    case ScPrefixExpr(operation, operand) => operation.refName == "!" && isOfBooleanType(operand)
    case ScInfixExpr(left, oper, right) =>
      boolInfixOperations.contains(oper.refName) &&
        isOfBooleanType(left) && isOfBooleanType(right)
    case _ => false
  }

  def isOfBooleanType(expr: ScExpression): Boolean = {
    import expr.projectContext
    implicit val context: Context = Context(expr)

    expr.`type`().getOrAny.weakConforms(api.Boolean)
  }

  private def getScExprChildren(expr: ScExpression) = expr.children.collect { case expr: ScExpression => expr }.toList

  private def booleanConst(expr: ScExpression): Option[Boolean] = expr match {
    case literal: ScLiteral =>
      literal.getText match {
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }
    case _ => None
  }

  private def simplifyTrivially(expr: ScExpression): ScExpression = expr match {
    case parenthesized: ScParenthesisedExpr =>
      val copy = parenthesized.copy.asInstanceOf[ScParenthesisedExpr]
      copy.replaceExpression(copy.innerElement.getOrElse(copy), removeParenthesis = true)
    case ScPrefixExpr(operation, operand) =>
      if (operation.refName != "!") expr
      else {
        booleanConst(operand) match {
          case Some(bool: Boolean) =>
            createExpressionFromText((!bool).toString, expr)(expr.getManager)
          case None => expr
        }
      }
    case ScInfixExpr(leftExpr, operation, rightExpr) =>
      val operName = operation.refName
      if (!boolInfixOperations.contains(operName)) expr
      else {
        booleanConst(leftExpr) match {
          case Some(bool: Boolean) => simplifyInfixWithLiteral(bool, operName, rightExpr)
          case None => booleanConst(rightExpr) match {
            case Some(bool: Boolean) => simplifyInfixWithLiteral(bool, operName, leftExpr)
            case None => expr
          }
        }
      }
    case _ => expr
  }

  private def simplifyInfixWithLiteral(value: Boolean, operation: String, expr: ScExpression): ScExpression = {
    implicit val projectContext: ProjectContext = expr.projectContext
    val text: String = booleanConst(expr) match {
      case Some(bool: Boolean) =>
        val result: Boolean = operation match {
          case "==" => bool == value
          case "!=" | "^" => bool != value
          case "&&" | "&" => bool && value
          case "||" | "|" => bool || value
        }
        result.toString
      case _ => (value, operation) match {
        case (true, "==") | (false, "!=") | (false, "^") | (true, "&&") | (true, "&") | (false, "||") | (false, "|") => expr.getText
        case (false, "==") | (true, "!=") | (true, "^") =>
          val negated: ScPrefixExpr = createExpressionFromText("!a", expr).asInstanceOf[ScPrefixExpr]
          val copyExpr = expr.copy.asInstanceOf[ScExpression]
          negated.operand.replaceExpression(copyExpr, removeParenthesis = true)
          negated.getText
        case (true, "||") | (true, "|") =>
          ScalaKeyword.TRUE
        case (false, "&&") | (false, "&") =>
          ScalaKeyword.FALSE
        case _ => throw new IllegalArgumentException("Wrong operation")
      }
    }
    createExpressionFromText(text, expr)
  }
}
