package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInfixElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  */
trait ScInfixExpr extends ScExpression with ScSugarCallExpr with ScInfixElement {

  import ScInfixExpr._

  type Kind = ScExpression
  type Reference = ScReferenceExpression

  override def left: ScExpression = unapply._1

  override def operation: ScReferenceExpression = extractOperationRef(unapply._2)

  override def rightOption: Option[ScExpression] = Option(right)

  def right: ScExpression = unapply._3

  def typeArgs: Option[ScTypeArgs] =
    getInvokedExpr.asOptionOf[ScGenericCall].map(_.typeArgs)

  override def getBaseExpr: ScExpression = {
    val withAssoc(base, _, _) = this
    base
  }

  override def getInvokedExpr: ScExpression = unapply._2

  override def argsElement: ScExpression = {
    val withAssoc(_, _, argument) = this
    argument
  }

  def isAssignmentOperator: Boolean =
    ParserUtils.isAssignmentOperator(operation.getText)

  def isAssignment: Boolean = isAssignmentOperator && (
    operation.textMatches("=") ||
      operation.multiResolveScala(incomplete = false).exists {
        case ScalaResolveResult(element, _) => element.name + "=" == operation.refName
      }
  )

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitInfixExpression(this)
  }

  private def unapply: (ScExpression, ScExpression, ScExpression) =
    findChildren[ScExpression] match {
      case Seq(left, operation, right) => (left, operation, right)
      case _                           => malformedInfixExpr(getText)
    }
}

object ScInfixExpr {
  private def malformedInfixExpr(text: String): Nothing =
    throw new RuntimeException("Malformed infix expr: " + text)

  private def extractOperationRef(invoked: ScExpression): ScReferenceExpression = invoked match {
    case ref: ScReferenceExpression => ref
    case ScGenericCall(ref, _)      => ref
    case _                          => malformedInfixExpr(invoked.getParent.getText)
  }

  def unapply(expression: ScInfixExpr): Option[(ScExpression, ScReferenceExpression, ScExpression)] =
    Option(expression.unapply).map { case (l, invoked, r) => (l, extractOperationRef(invoked), r) }

  object withAssoc {

    def unapply(expression: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] = {
      val (left, invoked, right) = expression.unapply
      val op = extractOperationRef(invoked)

      if (expression.isRightAssoc) Some(right, op, left)
      else                         Some(left, op, right)
    }
  }
}