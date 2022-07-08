package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiElement, PsiField, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor

trait ScAssignment extends ScExpression {
  def leftExpression: ScExpression = findChild[ScExpression].get

  def rightExpression: Option[ScExpression] = findLastChild[ScExpression] match {
    case Some(expr: ScExpression) if expr != leftExpression => Some(expr)
    case _ => None
  }

  def referenceName: Option[String] = {
    leftExpression match {
      case ref: ScReferenceExpression if ref.qualifier.isEmpty => Some(ref.getText)
      case _ => None
    }
  }

  def isNamedParameter: Boolean = {
    leftExpression match {
      case expr: ScReferenceExpression =>
        expr.bind() match {
          case Some(r) => r.isNamedParameter
          case _ => false
        }
      case _ => false
    }
  }

  def mirrorMethodCall: Option[ScMethodCall]

  /**
    * Has sense only in case if left token resolves to parameterless function
    *
    * @return parameterless function setter, or None otherwise
    */
  def resolveAssignment: Option[ScalaResolveResult]

  def shapeResolveAssignment: Option[ScalaResolveResult]

  /**
    * @return element to which equals sign should navigate
    */
  def assignNavigationElement: PsiElement = {
    leftExpression match {
      case methodCall: ScMethodCall =>
        methodCall.applyOrUpdateElement match {
          case Some(r) => r.getActualElement
          case None => null
        }
      case left => resolveAssignment match {
        case Some(ScalaResolveResult(elem, _)) => elem
        case _ => left match {
          case ref: ScReferenceExpression => ref.resolve() match {
            case v: ScVariable => v
            case p: ScClassParameter if p.isVar => p
            case f: PsiField => f
            case _ => null
          }
          case _ => null
        }
      }
    }
  }

  def isDynamicNamedAssignment: Boolean = {
    getContext match {
      case context@(_: ScTuple | _: ScParenthesisedExpr | _: ScArgumentExprList) =>
        context.getContext match {
          case m: MethodInvocation if m.argumentExpressions.contains(this) =>
            m.getEffectiveInvokedExpr match {
              case r: ScReferenceExpression =>
                r.bind() match {
                  case Some(resolveResult) if DynamicResolveProcessor.isApplyDynamicNamed(resolveResult)  => return true
                  case _ =>
                    m.applyOrUpdateElement match {
                      case Some(innerResult) if DynamicResolveProcessor.isApplyDynamicNamed(innerResult) => return true
                      case _ =>
                    }
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    false
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitAssignment(this)
  }
}

object ScAssignment {
  def unapply(st: ScAssignment): Option[(ScExpression, Option[ScExpression])] =
    Some(st.leftExpression, st.rightExpression)

  object Named {
    def unapply(st: ScAssignment): Option[String] = st.referenceName
  }

  object resolvesTo {
    def unapply(st: ScAssignment): Option[PsiNamedElement] = st.resolveAssignment.map(_.element)
  }

  implicit class ScAssignmentExt(private val target: ScAssignment) extends AnyVal {
    def assignmentToken: Option[PsiElement] = target.findFirstChildByType(ScalaTokenTypes.tASSIGN)
  }
}