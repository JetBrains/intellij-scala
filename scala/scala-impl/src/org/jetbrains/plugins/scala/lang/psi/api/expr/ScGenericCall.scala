package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScGenericCall extends ScExpression {

  def referencedExpr: ScExpression = findChild[ScExpression].get

  def typeArgs: ScTypeArgs = findChild[ScTypeArgs].get

  def arguments: Seq[ScTypeElement] = typeArgs.typeArgs

  def shapeType: TypeResult

  def shapeMultiType: Array[TypeResult]

  def shapeMultiResolve: Option[Array[ScalaResolveResult]]

  def multiType: Array[TypeResult]

  def multiResolve: Option[Array[ScalaResolveResult]]

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

