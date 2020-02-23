package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInfixElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs

/**
  * @author Alexander Podkhalyuzin
  */
trait ScInfixExpr extends ScExpression with ScSugarCallExpr with ScInfixElement {

  import ScInfixExpr._

  type Kind = ScExpression
  type Reference = ScReferenceExpression

  override def left: ScExpression = unapply._1

  override def operation: ScReferenceExpression = unapply._2

  override def rightOption: Option[ScExpression] = Option(right)

  def right: ScExpression = unapply._3

  def typeArgs: Option[ScTypeArgs] = findChildrenByClassScala(classOf[ScTypeArgs]) match {
    case Array(args) => Some(args)
    case _ => None
  }

  override def getBaseExpr: ScExpression = {
    val withAssoc(base, _, _) = this
    base
  }

  override def getInvokedExpr: ScExpression = operation

  override def argsElement: ScExpression = {
    val withAssoc(_, _, argument) = this
    argument
  }

  def isAssignmentOperator: Boolean =
    ParserUtils.isAssignmentOperator(operation.getText)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitInfixExpression(this)
  }

  private def unapply: (ScExpression, ScReferenceExpression, ScExpression) = {
    findChildrenByClassScala(classOf[ScExpression]) match {
      case Array(left, operation: ScReferenceExpression, right) =>
        (left, operation, right)
      case _ =>
        throw new RuntimeException("Wrong infix expression: " + getText)
    }
  }
}

object ScInfixExpr {

  def unapply(expression: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] =
    Some(expression.unapply)

  object withAssoc {

    def unapply(expression: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] = {
      val (left, operation, right) = expression.unapply

      if (expression.isRightAssoc) Some(right, operation, left)
      else Some(left, operation, right)
    }
  }

}