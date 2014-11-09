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
import scala.collection.mutable.ListBuffer

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
    if (bindingExpr != None) return Some(this)

    @tailrec
    def go(expr: PsiElement, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getContext match {
        case args: ScArgumentExprList => {
          if (!calcArguments) return Some(expr.asInstanceOf[ScExpression])
          args.getContext match {
            case call: ScMethodCall => go(call, calcArguments = false)
            case constr: ScConstructor => {
              PsiTreeUtil.getContextOfType(constr, true, classOf[ScNewTemplateDefinition]) match {
                case null => None
                case n: ScNewTemplateDefinition => go(n, calcArguments = false)
              }
            }
            case _ => None
          }
        }
        case inf: ScInfixExpr => go(inf, calcArguments = false)
        case pre: ScPrefixExpr => go(pre, calcArguments = false)
        case post: ScPostfixExpr => go(post, calcArguments = false)
        case ref: ScReferenceExpression => go(ref, calcArguments = false)
        case call: ScMethodCall => go(call, calcArguments = false)
        case gen: ScGenericCall => go(gen, calcArguments = false)
        case assign: ScAssignStmt if assign.getLExpression == expr => go(assign, calcArguments = false)
        case assign: ScAssignStmt if assign.getRExpression == Some(expr) && isUnderscore(expr) =>
          go(assign, calcArguments = false)
        case x: ScExpression if calcArguments => Some(x)
        case x: ScMatchStmt if !calcArguments => Some(x)
        case x: ScExpression if !calcArguments => {
          expr match {
            case _: ScUnderscoreSection => None
            case expr: ScExpression => Some(expr)
            case _ => None
          }
        }
        case _ => expr match {
          case x: ScUnderscoreSection => None
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
}

object ScUnderScoreSectionUtil {
  @tailrec
  def isUnderscore(expr: PsiElement): Boolean = {
    expr match {
      case u: ScUnderscoreSection => true
      case t: ScTypedStmt => t.expr.isInstanceOf[ScUnderscoreSection]
      case p: ScParenthesisedExpr =>
        p.expr match {
          case Some(expr) => isUnderscore(expr)
          case _ => false
        }
      case _ => false
    }
  }

  def isUnderscoreFunction(expr: PsiElement) = underscores(expr).length > 0

  def underscores(expr: PsiElement): Seq[ScUnderscoreSection] = {
    if (expr.getText.indexOf('_') == -1) return Seq.empty
    def inner(innerExpr: PsiElement): Seq[ScUnderscoreSection] = {
      innerExpr match {
        case under: ScUnderscoreSection => {
          under.bindingExpr match {
            case Some(e) => return Seq.empty
            case _ =>
          }
          val over = under.overExpr
          over match {
            case Some(e) if expr == e =>
              Seq(under)
            case _ => Seq.empty
          }
        }
        case _ =>
          val res = new ListBuffer[ScUnderscoreSection]
          val children = innerExpr.getChildren
          var i = 0
          while (i < children.length) {
            val in = inner(children(i))
            if (in.length > 1) {
              res ++= in
            } else if (in.length == 1) {
              res += in(0)
            }
            i += 1
          }
          res.toSeq
      }
    }
    inner(expr)
  }
}