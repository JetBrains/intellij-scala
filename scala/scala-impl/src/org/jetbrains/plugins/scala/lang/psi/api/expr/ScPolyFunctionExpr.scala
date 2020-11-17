package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}

trait ScPolyFunctionExpr extends ScExpression with ScControlFlowOwner {
  def typeParameters : Seq[ScTypeParam]

  def typeParamClause: ScTypeParamClause

  def result: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPolyFunctionExpression(this)
  }
}

object ScPolyFunctionExpr {
  def unapply(it: ScPolyFunctionExpr): Some[(Seq[ScTypeParam], Option[ScExpression])] =
    Some(it.typeParameters, it.result)
}