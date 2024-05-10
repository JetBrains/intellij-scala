package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil.isUnderscore

import scala.annotation.tailrec
import scala.collection.mutable

trait ScUnderscoreSection extends ScExpression {
  def bindingExpr: Option[ScExpression] =
    findChild[ScExpression]

  def overExpr: Option[ScExpression] = {
    if (bindingExpr.isDefined) return Some(this)

    @tailrec
    def go(expr: ScExpression, calcArguments: Boolean = true): Option[ScExpression] = {
      expr.getContext match {
        case args: ScArgumentExprList =>
          if (!calcArguments) return Some(expr)
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
  private val LOG = Logger.getInstance(classOf[ScUnderScoreSectionUtil.type])

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
  def underscores(expr: ScExpression): Seq[ScUnderscoreSection] = cachedInUserData("underscores", expr, ModTracker.anyScalaPsiChange, Tuple1(expr: ScExpression)) {
    if (!expr.isValid) Nil else {
      val underscores = Seq.newBuilder[ScUnderscoreSection]

      try collectUnderscoreNodes(expr.getNode, underscores)
      catch {
        case e: StackOverflowError =>
          // SCL-22522
          LOG.error(
            "Stackoverflow in collectUnderscoreNodes",
            e,
            new Attachment("underscore_section_code", expr.withParents.take(4).toSeq.last.getText)
          )
      }

      underscores
        .result()
        .filter(u => u.bindingExpr.isEmpty && u.overExpr.contains(expr))
    }
  }

  private def collectUnderscoreNodes(node: ASTNode, result: mutable.Growable[ScUnderscoreSection]): Unit = {
    node match {
      case composite: CompositeElement =>
        var currentChild = composite.getFirstChildNode
        while (currentChild != null) {
          if (currentChild.getElementType == ScalaElementType.PLACEHOLDER_EXPR)
            result.addOne(currentChild.getPsi(classOf[ScUnderscoreSection]))
          else
            collectUnderscoreNodes(currentChild, result)

          currentChild = currentChild.getTreeNext
        }
      case _ =>
    }
  }
}
