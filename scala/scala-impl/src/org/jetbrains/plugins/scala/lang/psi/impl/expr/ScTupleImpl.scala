package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Singleton, TupleType, Unit}
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

        val widenedComponents = components.zipWithIndex.map {
          case (lit: ScLiteralType, idx) =>
            val expectedComp   = expectedComponents.lift(idx)
            val inferSingleton = expectedComp.exists(_.conforms(Singleton))

            if (inferSingleton) lit
            else                lit.widen
          case (other, _) => other
        }

        TupleType(widenedComponents)
    })

  override def deleteChildInternal(child: ASTNode): Unit = {
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
  }

  override def toString: String = "Tuple"
}
