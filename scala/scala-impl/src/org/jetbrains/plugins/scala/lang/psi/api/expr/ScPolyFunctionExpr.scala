package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}

trait ScPolyFunctionExprBase extends ScExpressionBase with ScControlFlowOwnerBase { this: ScPolyFunctionExpr =>
  def typeParameters : Seq[ScTypeParam]

  def typeParamClause: ScTypeParamClause

  def result: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPolyFunctionExpression(this)
  }
}

abstract class ScPolyFunctionExprCompanion {
  def unapply(it: ScPolyFunctionExpr): Some[(Seq[ScTypeParam], Option[ScExpression])] =
    Some(it.typeParameters, it.result)
}