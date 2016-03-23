package org.jetbrains.plugins.scala
package codeInspection.booleans

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText

import scala.Predef._


/**
 * Nikolay.Tropin
 * 4/23/13
 *
 */
class SimplifyBooleanInspection extends AbstractInspection("SimplifyBoolean", "Simplify boolean expression"){

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case _: ScParenthesisedExpr => //do nothing to avoid many similar expressions
    case expr: ScExpression if SimplifyBooleanUtil.canBeSimplified(expr) =>
        holder.registerProblem(expr, "Simplify boolean expression", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new SimplifyBooleanQuickFix(expr))
  }

}

class SimplifyBooleanQuickFix(expr: ScExpression) extends AbstractFixOnPsiElement("Simplify " + getShortText(expr), expr) {

  def doApplyFix(project: Project) {
    val scExpr = getElement
    if (scExpr.isValid && SimplifyBooleanUtil.canBeSimplified(scExpr)) {
      val simplified = SimplifyBooleanUtil.simplify(scExpr)
      scExpr.replaceExpression(simplified, removeParenthesis = true)
    }
  }
}

object SimplifyBooleanUtil {
  val boolInfixOperations = Set("==", "!=", "&&", "&", "||", "|", "^")

  def canBeSimplified(expr: ScExpression, isTopLevel: Boolean = true): Boolean = {
    expr match {
      case _: ScLiteral if !isTopLevel => booleanConst(expr).isDefined
      case ScParenthesisedExpr(e) => canBeSimplified(e, isTopLevel)
      case expression: ScExpression =>
        val children = getScExprChildren(expr)
        val isBooleanOperation = expression match {
          case ScPrefixExpr(operation, operand) => operation.refName == "!" && isOfBooleanType(operand)
          case ScInfixExpr(left, oper, right) =>
            boolInfixOperations.contains(oper.refName) &&
                    isOfBooleanType(left) && isOfBooleanType(right)
          case _ => false
        }
        isBooleanOperation && isOfBooleanType(expr) && children.exists(canBeSimplified(_, isTopLevel = false))
    }
  }

  def simplify(expr: ScExpression, isTopLevel: Boolean = true): ScExpression = {
    if (canBeSimplified(expr, isTopLevel) && booleanConst(expr).isEmpty) {
      val exprCopy = ScalaPsiElementFactory.createExpressionWithContextFromText(expr.getText, expr.getContext, expr)
      val children = getScExprChildren(exprCopy)
      children.foreach(child => exprCopy.getNode.replaceChild(child.getNode, simplify(child, isTopLevel = false).getNode))
      simplifyTrivially(exprCopy)
    }
    else expr
  }

  private def isOfBooleanType(expr: ScExpression)
                             (implicit typeSystem: TypeSystem = expr.typeSystem): Boolean = {
    expr.getType(TypingContext.empty).getOrAny.weakConforms(lang.psi.types.Boolean)
  }

  private def getScExprChildren(expr: ScExpression) =  expr.children.collect { case expr: ScExpression => expr }.toList

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
      copy.replaceExpression(copy.expr.getOrElse(copy), removeParenthesis = true)
    case ScPrefixExpr(operation, operand) =>
      if (operation.refName != "!") expr
      else {
        booleanConst(operand) match {
          case Some(bool: Boolean) =>
            ScalaPsiElementFactory.createExpressionFromText((!bool).toString, expr.getManager)
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
    val manager = expr.getManager
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
        case (true, "==") | (false, "!=") | (false, "^") | (true, "&&") | (true, "&") | (false, "||") | (false, "|")  => expr.getText
        case (false, "==") | (true, "!=") | (true, "^") =>
          val negated: ScPrefixExpr = ScalaPsiElementFactory.createExpressionFromText("!a", manager).asInstanceOf[ScPrefixExpr]
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
    ScalaPsiElementFactory.createExpressionFromText(text, manager)

  }
}
