package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ScGenericCall extends ScExpression {

  def referencedExpr: ScExpression = findChild[ScExpression].get

  def typeArgs: ScTypeArgs = findChild[ScTypeArgs].get

  def arguments: Seq[ScTypeElement] = typeArgs.typeArgs

  def shapeType: TypeResult

  def shapeMultiType: Array[TypeResult]

  def shapeMultiResolve: Option[Array[ScalaResolveResult]]

  def multiType: Array[TypeResult]

  def multiResolve: Option[Array[ScalaResolveResult]]

  def bindInvokedExpr: Option[ScalaResolveResult]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenericCallExpression(this)
  }
}

object ScGenericCall {

  def unapply(call: ScGenericCall): Option[(ScReferenceExpression, Seq[ScTypeElement])] =
    Option(call.referencedExpr).collect {
      case reference: ScReferenceExpression => (reference, call.arguments)
    }
}

