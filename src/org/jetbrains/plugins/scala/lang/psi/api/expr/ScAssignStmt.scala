package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiElement, PsiElementVisitor}
import resolve.{ResolvableReferenceElement, ScalaResolveResult}


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
          case Some(r) if r.isNamedParameter => true
          case _ => false
        }
      case _ => false
    }
  }

  def mirrorMethodCall: Option[ScMethodCall]

  def resolveAssignment: Option[ScalaResolveResult]

  def shapeResolveAssignment: Option[ScalaResolveResult]

  def assignNavigationElement: PsiElement = {
    getLExpression match {
      case methodCall: ScMethodCall =>
        methodCall.applyOrUpdateElement match {
          case Some(arrayOrUpdateElement) => arrayOrUpdateElement
          case None => null
        }
      case _ => resolveAssignment match {
        case Some(ScalaResolveResult(elem, _)) => elem
        case _ => null
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