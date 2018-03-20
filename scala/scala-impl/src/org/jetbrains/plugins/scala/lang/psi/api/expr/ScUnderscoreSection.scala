package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil.isUnderscore

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
trait ScUnderscoreSection extends ScExpression {
  def bindingExpr: Option[ScExpression] = {
    findChildByClassScala(classOf[ScExpression]) match {
      case null => None
      case expression: ScExpression => Some(expression)
    }
  }

  def overExpr: Option[ScExpression] = {
    if (bindingExpr.isDefined) return Some(this)

    @tailrec
    def go(expr: PsiElement, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getContext match {
        case args: ScArgumentExprList =>
          if (!calcArguments) return Some(expr.asInstanceOf[ScExpression])
          args.getContext match {
            case call: ScMethodCall => go(call, calcArguments = false)
            case constr: ScConstructor =>
              PsiTreeUtil.getContextOfType(constr, true, classOf[ScNewTemplateDefinition]) match {
                case null => None
                case n: ScNewTemplateDefinition => go(n, calcArguments = false)
              }
            case _ => None
          }
        case tuple: ScTuple if calcArguments =>
          tuple.getContext match {
            case infix: ScInfixExpr if infix.argsElement == tuple => go(infix, calcArguments = false)
            case _ => Some(tuple)
          }
        case inf: ScInfixExpr => go(inf, calcArguments = false)
        case pre: ScPrefixExpr => go(pre, calcArguments = false)
        case post: ScPostfixExpr => go(post, calcArguments = false)
        case ref: ScReferenceExpression => go(ref, calcArguments = false)
        case call: ScMethodCall => go(call, calcArguments = false)
        case gen: ScGenericCall => go(gen, calcArguments = false)
        case assign: ScAssignStmt if assign.getLExpression == expr => go(assign, calcArguments = false)
        case assign: ScAssignStmt if assign.getRExpression.contains(expr) && isUnderscore(expr) =>
          go(assign, calcArguments = false)
        case _: ScEnumerator | _: ScGenerator if calcArguments =>
          go(ScalaPsiUtil.contextOfType(expr, strict = true, classOf[ScForStatement]), calcArguments = false)
        case guard: ScGuard => go(ScalaPsiUtil.contextOfType(guard, strict = true, classOf[ScExpression]), calcArguments = false)
        case x: ScExpression if calcArguments => Some(x)
        case x: ScMatchStmt if !calcArguments => Some(x)
        case x: ScTypedStmt if !calcArguments => Some(x)
        case _: ScExpression if !calcArguments =>
          expr match {
            case _: ScUnderscoreSection => None
            case expr: ScExpression => Some(expr)
            case _ => None
          }
        case _ => expr match {
          case _: ScUnderscoreSection => None
          case x: ScExpression => Some(x)
          case _ => None
        }
      }
    }

    @tailrec
    def removeParentheses(p: ScExpression): ScExpression = {
      p.getContext match {
        case p: ScParenthesisedExpr => removeParentheses(p)
        case _ => p
      }
    }

    getContext match {
      case t: ScTypedStmt => go(removeParentheses(t))
      case _ => go(removeParentheses(this))
    }
  }

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitUnderscoreExpression(this)
  }
}

object ScUnderScoreSectionUtil {
  @tailrec
  def isUnderscore(expr: PsiElement): Boolean = {
    expr match {
      case _: ScUnderscoreSection => true
      case t: ScTypedStmt => t.expr.isInstanceOf[ScUnderscoreSection]
      case p: ScParenthesisedExpr =>
        p.innerElement match {
          case Some(expression) => isUnderscore(expression)
          case _ => false
        }
      case _ => false
    }
  }

  def isUnderscoreFunction(expr: PsiElement): Boolean = underscores(expr).nonEmpty

  def underscores(expr: PsiElement): Seq[ScUnderscoreSection] = {
    if (expr.getText.indexOf('_') == -1) return Seq.empty
    def inner(innerExpr: PsiElement): Seq[ScUnderscoreSection] = {
      innerExpr match {
        case under: ScUnderscoreSection =>
          under.bindingExpr match {
            case Some(_) => return Seq.empty
            case _ =>
          }
          under.overExpr match {
            case Some(e) if expr == e =>
              Seq(under)
            case _ => Seq.empty
          }
        case _ =>
          innerExpr.getChildren.flatMap(inner)
      }
    }
    inner(expr)
  }
}
