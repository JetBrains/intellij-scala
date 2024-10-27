package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType}

trait ScPolyFunctionExpr extends ScExpression with ScControlFlowOwner with ScTypeParametersOwner {

  def result: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPolyFunctionExpression(this)
  }
}

object ScPolyFunctionExpr {
  def unapply(it: ScPolyFunctionExpr): Some[(Seq[ScTypeParam], Option[ScExpression])] =
    Some(it.typeParameters, it.result)

  private[psi] def propagateContextBounds(
    tpe: ScType,
    bounds: Seq[ScType]
  )(implicit scope: ElementScope): ScType = tpe match {
    case FunctionType(inner, args) => FunctionType((propagateContextBounds(inner, bounds), args))
    case other                     => ContextFunctionType((other, bounds))
  }
}