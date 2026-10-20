package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement.InnermostElement
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType}

import scala.annotation.tailrec

trait ScPolyFunctionExpr extends ScExpression with ScControlFlowOwner with ScTypeParametersOwner {

  def result: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPolyFunctionExpression(this)
  }
}

object ScPolyFunctionExpr {
  def unapply(it: ScPolyFunctionExpr): Some[(Seq[ScTypeParam], Option[ScExpression])] =
    Some(it.typeParameters, it.result)

  /**
   * The function literal that `body`, the body of a polymorphic function literal, consists of,
   * possibly in parentheses or in a block without braces, as which the body is parsed in a block,
   * e.g. `(a: A) => a` in `{ [A] => (a: A) => a }`.
   */
  @tailrec
  def functionLiteral(body: ScExpression): Option[ScFunctionExpr] = body match {
    case InnermostElement(function: ScFunctionExpr) => Some(function)
    case block: ScBlock if !block.hasLBrace =>
      block.statements match {
        case Seq(single: ScExpression) => functionLiteral(single)
        case _                         => None
      }
    case _ => None
  }

  private[psi] def propagateContextBounds(
    tpe: ScType,
    bounds: Seq[ScType]
  )(implicit scope: ElementScope, context: Context): ScType = tpe match {
    case FunctionType(inner, args) => FunctionType((propagateContextBounds(inner, bounds), args))
    case other                     => ContextFunctionType((other, bounds))
  }
}
