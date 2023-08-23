package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

trait ScPolyFunctionExpr extends ScExpression with ScControlFlowOwner with ScTypeParametersOwner {

  def result: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPolyFunctionExpression(this)
  }
}

object ScPolyFunctionExpr {
  def unapply(it: ScPolyFunctionExpr): Some[(Seq[ScTypeParam], Option[ScExpression])] =
    Some(it.typeParameters, it.result)
}