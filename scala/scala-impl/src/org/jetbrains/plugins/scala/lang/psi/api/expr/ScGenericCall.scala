package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.{InvocationDetailsOwner, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeArgument}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.{ResolveMode, ScalaResolveResult}

trait ScGenericCall extends ScExpression with InvocationDetailsOwner {

  def referencedExpr: ScExpression = findChild[ScExpression].get

  def typeArgs: ScTypeArgs = findChild[ScTypeArgs].get

  def typeArguments: Seq[ScTypeArgument] = typeArgs.typeArguments

  /**
   * @return types in the same order as [[multiResolve]]
   */
  def multiType(mode: ResolveMode = ResolveMode.Full): Array[TypeResult]

  /**
   * @return the results of resolving the referenced expression, if it is a reference, see [[ScReferenceExpression.resolveWithMode]]
   */
  def multiResolve(mode: ResolveMode = ResolveMode.Full): Option[Array[ScalaResolveResult]]

  def bindInvokedExpr: Option[ScalaResolveResult]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenericCallExpression(this)
  }
}

object ScGenericCall {
  def unapply(call: ScGenericCall): Option[(ScReferenceExpression, Seq[ScTypeArgument])] =
    Option(call.referencedExpr).collect {
      case reference: ScReferenceExpression => (reference, call.typeArguments)
    }
}

