package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
* @author Alexander Podkhalyuzin, ilyas
*/

trait ScFunctionExpr extends ScExpression with ScControlFlowOwner {

  def parameters: Seq[ScParameter]

  def params: ScParameters

  def result: Option[ScExpression]

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitFunctionExpression(this)
}

object ScFunctionExpr {
  def unapply(it: ScFunctionExpr): Some[(Seq[ScParameter], Option[ScExpression])] =
    Some(it.parameters, it.result)
}