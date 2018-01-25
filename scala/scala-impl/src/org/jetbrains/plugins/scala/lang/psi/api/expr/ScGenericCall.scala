package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScGenericCall extends ScExpression {

  def referencedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def typeArgs: Option[ScTypeArgs] = findChild(classOf[ScTypeArgs])

  def arguments: Seq[ScTypeElement] = (for (t <- typeArgs) yield t.typeArgs) match {
    case Some(x) => x
    case _ => Nil
  }

  def shapeType: TypeResult

  def shapeMultiType: Array[TypeResult]

  def shapeMultiResolve: Option[Array[ScalaResolveResult]]

  def multiType: Array[TypeResult]

  def multiResolve: Option[Array[ScalaResolveResult]]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenericCallExpression(this)
  }
}

object ScGenericCall {

  def unapply(call: ScGenericCall): Option[(ScReferenceExpression, Seq[ScTypeElement])] =
    Option(call.referencedExpr).collect {
      case reference: ScReferenceExpression => (reference, call.arguments)
    }
}

