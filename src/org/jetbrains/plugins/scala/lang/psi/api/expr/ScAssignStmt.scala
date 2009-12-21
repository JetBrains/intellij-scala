package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

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

  override def accept(visitor: ScalaElementVisitor) = visitor.visitAssignmentStatement(this)
}

object NamedAssignStmt {
  def unapply(st: ScAssignStmt): Option[String] = st.assignName
}