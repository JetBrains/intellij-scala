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
  def bindingExpr: Option[ScReferenceExpression] = {
    findChildByClassScala(classOf[ScReferenceExpression]) match {
      case null => None
      case ref: ScReferenceExpression => Some(ref)
    }
  }

  def overExpr: Option[ScExpression] = {
    if (bindingExpr != None) return Some(this)

    def go(expr: PsiElement, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getParent match {
        case args: ScArgumentExprList => {
          if (!calcArguments) return Some(expr.asInstanceOf[ScExpression])
          args.getParent match {
            case call: ScMethodCall => Some(call)
            case constr: ScConstructor => {
              PsiTreeUtil.getParentOfType(constr, classOf[ScNewTemplateDefinition]) match {
                case null => None
                case n: ScNewTemplateDefinition => Some(n)
              }
            }
            case _ => None
          }
        }
        case inf: ScInfixExpr => {
          var par = inf
          while (par.getParent != null && par.getParent.isInstanceOf[ScInfixExpr]) {
            par = par.getParent.asInstanceOf[ScInfixExpr]
          }
          Some(par)
        }
        case ref: ScReferenceExpression => go(ref, false)
        case call: ScMethodCall => go(call, false)
        case gen: ScGenericCall => go(gen, false)
        case x: ScExpression => Some(x) //todo: need to check this statement
        case _ => None
      }
    }
    getParent match {
      case t: ScTypedStmt => {
        t.getParent match {
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