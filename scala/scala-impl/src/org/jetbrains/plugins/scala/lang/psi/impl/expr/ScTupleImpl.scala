package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, Widening}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TupleType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTuple {

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    def expectedComponent(idx: Int): Option[ScType] =
      ExpectedTypePropagation.tupleComponent(this, idx, expectedType)

    // The components of a tuple, which is the argument of an infix call, are the arguments of the call,
    // so their expected types are not derived from the expected type of the tuple
    def componentType(component: ScExpression, idx: Int): TypeResult =
      if (isCall) component.`type`()
      else        component.`type`(expectedComponent(idx))

    Right(exprs.zipWithIndex.map { case (component, idx) => componentType(component, idx).getOrAny } match {
      case Seq() => Unit
      case components =>
        // The component types are inferred, so their literal types are widened unless the expected
        // component asks for a singleton, see Widening.widenInferred
        val widenedComponents = components.zipWithIndex.map {
          case (lit: ScLiteralType, idx) => Widening.widenInferred(lit, expectedComponent(idx))
          case (other, _)                => other
        }

        TupleType(widenedComponents, context = this)
    })
  }

  override def deleteChildInternal(child: ASTNode): Unit = {
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
  }

  override def toString: String = "Tuple"
}
