package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
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
    def go(expr: ScExpression, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getContext match {
        case args: ScArgumentExprList =>
          if (!calcArguments) return Some(expr.asInstanceOf[ScExpression])
          args.getContext match {
            case call: ScMethodCall => go(call, calcArguments = false)
            case constrInvocation: ScConstructorInvocation =>
              PsiTreeUtil.getContextOfType(constrInvocation, true, classOf[ScNewTemplateDefinition]) match {
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
        case assign: ScAssignment if assign.leftExpression == expr => go(assign, calcArguments = false)
        case assign: ScAssignment if assign.rightExpression.contains(expr) && isUnderscore(expr) =>
          go(assign, calcArguments = false)
        case _: ScForBinding | _: ScGenerator if calcArguments =>
          go(ScalaPsiUtil.contextOfType(expr, strict = true, classOf[ScFor]), calcArguments = false)
        case guard: ScGuard => go(ScalaPsiUtil.contextOfType(guard, strict = true, classOf[ScExpression]), calcArguments = false)
        case x: ScExpression if calcArguments => Some(x)
        case x: ScMatch if !calcArguments => Some(x)
        case x: ScTypedExpression if !calcArguments => Some(x)
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
      case t: ScTypedExpression => go(removeParentheses(t))
      case _ => go(removeParentheses(this))
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitUnderscoreExpression(this)
  }
}

object ScUnderscoreSection {
  object binding {
    def unapply(under: ScUnderscoreSection): Option[ScExpression] = under.bindingExpr
  }
}

object ScUnderScoreSectionUtil {
  @tailrec
  def isUnderscore(expr: ScExpression): Boolean = {
    expr match {
      case _: ScUnderscoreSection => true
      case t: ScTypedExpression => t.expr.isInstanceOf[ScUnderscoreSection]
      case p: ScParenthesisedExpr =>
        p.innerElement match {
          case Some(expression) => isUnderscore(expression)
          case _ => false
        }
      case _ => false
    }
  }

  def isUnderscoreFunction(expr: ScExpression): Boolean = underscores(expr).nonEmpty

  /**Collects parameters of anonymous functions in placeholder syntax*/
  def underscores(expr: ScExpression): Seq[ScUnderscoreSection] = {
    if (!expr.isValid)
      return Nil

    val expressionText = expr.getText
    val file = expr.getContainingFile
    val start = expr.startOffset

    var index = expressionText.lastIndexOf('_')
    var found: List[ScUnderscoreSection] = Nil

    while (index >= 0) {
      val leaf = file.findElementAt(start + index)
      leaf.getParent match {
        case u: ScUnderscoreSection if u.bindingExpr.isEmpty && u.overExpr.contains(expr) =>
          found = u :: found
        case _ =>
      }
      index = expressionText.lastIndexOf('_', index - 1)
    }

    found
  }
}
