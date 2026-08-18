package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, Widening}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TupleType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTuple {

  protected override def innerType: TypeResult =
    Right(exprs.map(_.`type`().getOrAny) match {
      case Seq() => Unit
      case components =>
        lazy val expectedComponents = this.expectedType() match {
          case Some(TupleType(comps)) => comps
          case _                      => Seq.empty
        }

        // The component types are inferred, so their literal types are widened unless the expected
        // component asks for a singleton, see Widening.widenInferred
        val widenedComponents = components.zipWithIndex.map {
          case (lit: ScLiteralType, idx) => Widening.widenInferred(lit, expectedComponents.lift(idx))
          case (other, _)                => other
        }

        TupleType(widenedComponents, context = this)
    })

  override def deleteChildInternal(child: ASTNode): Unit = {
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
  }

  override def toString: String = "Tuple"
}
