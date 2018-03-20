package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInfixElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author Alexander Podkhalyuzin
  */
trait ScInfixExpr extends ScExpression with ScSugarCallExpr with ScInfixElement[ScExpression, ScReferenceExpression] {

  import ScInfixExpr._

  def lOp: ScExpression = unapply._1

  override def operation: ScReferenceExpression = unapply._2

  def rOp: ScExpression = unapply._3

  override def leftOperand: ScExpression = lOp

  override def rightOperand: Option[ScExpression] = Some(rOp)


  def typeArgs: Option[ScTypeArgs] = findChildrenByClassScala(classOf[ScTypeArgs]) match {
    case Array(args) => Some(args)
    case _ => None
  }

  def getBaseExpr: ScExpression = {
    val withAssoc(base, _, _) = this
    base
  }

  def getInvokedExpr: ScExpression = operation

  def argsElement: ScExpression = {
    val withAssoc(_, _, argument) = this
    argument
  }

  def isAssignmentOperator: Boolean =
    ParserUtils.isAssignmentOperator(operation.getText)

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitInfixExpression(this)
  }

  private def unapply = findChildrenByClassScala(classOf[ScExpression]) match {
    case Array(left, operation: ScReferenceExpression, right) =>
      (left, operation, right)
    case _ => throw new RuntimeException("Wrong infix expression: " + getText)
  }
}

object ScInfixExpr {

  def unapply(expression: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] =
    Some(expression.unapply)

  object withAssoc {

    def unapply(expression: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] = {
      val (left, operation, right) = expression.unapply

      if (ScalaNamesUtil.clean(operation.refName).endsWith(":")) Some(right, operation, left)
      else Some(left, operation, right)
    }
  }

}