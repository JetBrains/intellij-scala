package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiField, PsiElement}
import resolve.ScalaResolveResult
import statements.ScVariable
import statements.params.ScClassParameter


/**
 * @author Alexander Podkhalyuzin
 */

trait ScAssignStmt extends ScExpression {
  def getLExpression: ScExpression = findChildByClassScala(classOf[ScExpression])

  def getRExpression: Option[ScExpression] = findLastChild(classOf[ScExpression]) match {
    case Some(expr: ScExpression) if expr != getLExpression => Some(expr)
    case _ => None
  }

  def assignName: Option[String] = {
    getLExpression match {
      case ref: ScReferenceExpression if ref.qualifier == None => Some(ref.getText)
      case _ => None
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitAssignmentStatement(this)
  }

  def isNamedParameter: Boolean = {
    getLExpression match {
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
   * @return parameterless function setter, or None otherwise
   */
  def resolveAssignment: Option[ScalaResolveResult]

  def shapeResolveAssignment: Option[ScalaResolveResult]

  /**
   * @return element to which equals sign should navigate
   */
  def assignNavigationElement: PsiElement = {
    getLExpression match {
      case methodCall: ScMethodCall =>
        methodCall.applyOrUpdateElement match {
          case Some(arrayOrUpdateElement) => arrayOrUpdateElement
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
}

object NamedAssignStmt {
  def unapply(st: ScAssignStmt): Option[String] = st.assignName
}

object ScAssignStmt {
  def unapply(st: ScAssignStmt): Option[(ScExpression, Option[ScExpression])] =
    Some(st.getLExpression, st.getRExpression)
}