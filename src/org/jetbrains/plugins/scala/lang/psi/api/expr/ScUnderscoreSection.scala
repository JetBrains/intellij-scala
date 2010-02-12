package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.ScConstructor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement

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

    def go(expr: PsiElement, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getContext match {
        case args: ScArgumentExprList => {
          if (!calcArguments) return Some(expr.asInstanceOf[ScExpression])
          args.getContext match {
            case call: ScMethodCall => Some(call)
            case constr: ScConstructor => {
              PsiTreeUtil.getContextOfType(constr, classOf[ScNewTemplateDefinition], true) match {
                case null => None
                case n: ScNewTemplateDefinition => Some(n)
              }
            }
            case _ => None
          }
        }
        case inf: ScInfixExpr => go(inf, false)
        case pre: ScPrefixExpr => go(pre, false)
        case post: ScPostfixExpr => go(post, false)
        case ref: ScReferenceExpression => go(ref, false)
        case call: ScMethodCall => go(call, false)
        case gen: ScGenericCall => go(gen, false)
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
    getContext match {
      case t: ScTypedStmt => {
        t.getContext match {
          case p: ScParenthesisedExpr => go(p)
          case _ => go(t)
        }
      }
      case _ => go(this)
    }
  }
}

object ScUnderScoreSectionUtil {
  def underscores(expr: PsiElement): Seq[ScUnderscoreSection] = {
    def inner(innerExpr: PsiElement): Seq[ScUnderscoreSection] = {
      innerExpr match {
        case under: ScUnderscoreSection => {
          under.bindingExpr match {
            case Some(e) => return Seq.empty
            case _ =>
          }
          under.overExpr match {
            case Some(e) if expr == e => Seq(under)
            case _ => Seq.empty
          }
        }
        case _ => innerExpr.getChildren.toSeq.flatMap(inner(_))
      }
    }
    inner(expr)
  }
}